package com.fptis.intern.server.application.payment;

import com.fptis.intern.server.domain.payment.Payment;
import com.fptis.intern.server.domain.payment.PaymentProvider;
import com.fptis.intern.server.domain.payment.PaymentRepository;
import com.fptis.intern.server.domain.payment.PaymentStatus;
import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * discussion#16(방안 2: Hold→Pay 분리)의 결제 승인 단계만 담당한다. 재고/슬롯 홀드는
 * ReservationHoldService가 예약 생성 시점에 이미 잡아두므로, 이 서비스는 Payment 기록과 그에 따른
 * Reservation 상태 전이(PENDING_PAYMENT -> RESERVED)만 다룬다 — ReservationService에는 의존하지
 * 않고 ReservationRepository만 사용해 단방향 의존을 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final PaymentProvider DEFAULT_PROVIDER = PaymentProvider.STRIPE;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    /**
     * 예약 홀드가 커밋되어 재고/슬롯 락이 이미 풀린 뒤에만 호출해야 한다 — Stripe PaymentIntent
     * 생성은 외부 HTTP 호출이라 ReservationHoldService의 락 트랜잭션 안에서 부르면 discussion#16이
     * 버린 방안 1(락을 PG 응답 속도에 종속시킴)과 똑같아진다.
     * TODO(#26): amount/currencyCode는 아직 외화 표시 금액이다 — 실제 청구해야 할 KRW 금액은
     * 기준 환율(Currency 도메인) 연동 전까지 계산할 수 없어 자리표시자로 그대로 전달한다.
     */
    @Transactional
    public PaymentIntentResult createPaymentIntent(Reservation reservation) {
        Payment payment = Payment.initiate(reservation.getId(), DEFAULT_PROVIDER, reservation.getReservationNumber(),
                reservation.getAmount(), reservation.getCurrencyCode(), LocalDateTime.now());
        paymentRepository.save(payment);

        long amountMinorUnits = StripeAmountConverter.toMinorUnits(reservation.getAmount(), reservation.getCurrencyCode());
        Map<String, String> metadata = Map.of(
                "reservationId", String.valueOf(reservation.getId()),
                "reservationNumber", reservation.getReservationNumber());

        PaymentIntentResult result = paymentGateway.createIntent(payment.getIdempotencyKey(), amountMinorUnits,
                reservation.getCurrencyCode(), metadata);

        payment.attachIntent(result.paymentIntentId());
        return result;
    }

    /**
     * Stripe 웹훅(payment_intent.succeeded) 핸들러. Stripe는 이벤트를 최소 1회 이상 재전송할 수
     * 있으므로(중복/순서 뒤바뀜) 반드시 멱등해야 하고, 예외를 던지면 안 된다 — 던지면 웹훅 응답이
     * 5xx가 되어 Stripe가 같은 이벤트를 계속 재시도한다.
     */
    @Transactional
    public void handlePaymentSucceeded(String pgPaymentIntentId) {
        Payment payment = paymentRepository.findByPgPaymentIntentId(pgPaymentIntentId).orElse(null);
        if (payment == null) {
            log.warn("[PaymentService] 알 수 없는 PaymentIntent에 대한 succeeded 이벤트: {}", pgPaymentIntentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.APPROVED) {
            return; // 같은 이벤트 재전송 — 멱등 no-op.
        }

        Reservation reservation = reservationRepository.findById(payment.getReservationId()).orElse(null);
        if (reservation == null) {
            log.error("[PaymentService] Payment는 있는데 Reservation이 없음 — reservationId={}, pgPaymentIntentId={}",
                    payment.getReservationId(), pgPaymentIntentId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            // discussion#16이 명시한 레이스: 결제 TTL을 넘겨 홀드가 이미 EXPIRED로 풀린(재고/슬롯이
            // 다른 손님에게 넘어갔을 수 있는) 뒤에 승인 웹훅이 뒤늦게 도착한 경우다. 여기서 조용히
            // 예약을 되살리면 초과판매가 될 수 있으므로 자동 복구하지 않고, payments.status=APPROVED
            // 인데 reservations.status가 RESERVED가 아닌 조합으로 남겨 운영 수동 정산(환불 또는
            // 별도 슬롯 배정) 대상으로 표시만 한다.
            payment.markApproved(now);
            log.error("[PaymentService] 홀드가 이미 종료된 뒤 결제 승인 웹훅 수신 — 수동 정산 필요. "
                    + "reservationId={}, reservationStatus={}, pgPaymentIntentId={}",
                    reservation.getId(), reservation.getStatus(), pgPaymentIntentId);
            return;
        }

        payment.markApproved(now);
        reservation.confirmPayment(now);
        reservation.issueQrToken(generateQrToken());
    }

    /**
     * Stripe 웹훅(payment_intent.payment_failed) 핸들러. 카드 거절 등은 Reservation 상태를 바꾸지
     * 않는다 — 슬롯은 계속 홀드된 채로 사용자가 결제 TTL 안에서 다른 수단으로 재시도할 수 있다.
     */
    @Transactional
    public void handlePaymentFailed(String pgPaymentIntentId) {
        paymentRepository.findByPgPaymentIntentId(pgPaymentIntentId)
                .ifPresentOrElse(
                        payment -> payment.markFailed(LocalDateTime.now()),
                        () -> log.warn("[PaymentService] 알 수 없는 PaymentIntent에 대한 payment_failed 이벤트: {}", pgPaymentIntentId));
    }

    private String generateQrToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
