package com.fptis.intern.server.application.reservation;

import com.fptis.intern.server.application.payment.PaymentIntentResult;
import com.fptis.intern.server.application.payment.PaymentService;
import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchTimeSlot;
import com.fptis.intern.server.domain.branch.BranchTimeSlotRepository;
import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.branch.dto.BranchSummaryResponse;
import com.fptis.intern.server.presentation.reservation.dto.RedeemRequest;
import com.fptis.intern.server.presentation.reservation.dto.RedeemResponse;
import com.fptis.intern.server.presentation.reservation.dto.ReservationCreateRequest;
import com.fptis.intern.server.presentation.reservation.dto.ReservationDetailResponse;
import com.fptis.intern.server.presentation.reservation.dto.ReservationPageResponse;
import com.fptis.intern.server.presentation.reservation.dto.ReservationSummaryResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private static final double AMOUNT_LIMIT_USD = 10_000;
    private static final double AMOUNT_MIN_VND = 10_000;

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final BranchCurrencyRateRepository branchCurrencyRateRepository;
    private final BranchTimeSlotRepository branchTimeSlotRepository;
    private final ReservationHoldService reservationHoldService;
    private final PaymentService paymentService;
    private final CurrencyRepository currencyRepository;

    /**
     * discussion#41 방안 2 — cancelReservation/rejectByBranch/redeem은 실제 상태 변경을
     * self를 통해 호출해서, 낙관적 락 충돌({@link ObjectOptimisticLockingFailureException})이
     * 나면 그 내부 트랜잭션(REQUIRES_NEW)만 롤백되고, 바깥(이 메서드들)에서 새 트랜잭션으로
     * 재조회해 충돌을 판정할 수 있게 한다. 같은 빈의 self-invocation은 프록시를 안 거쳐
     * @Transactional이 무시되므로, 지연 주입한 자기 자신을 통해 호출해야 한다.
     */
    @Lazy
    private final ReservationService self;

    /**
     * `docs/discussion-reservation-transaction-boundary.md`의 Phase 0/3 — 이 메서드 자체는
     * 트랜잭션을 열지 않는다(`NOT_SUPPORTED`로 클래스 기본값 readOnly=true를 명시적으로 덮어씀).
     * 아래에서 부르는 {@code reservationHoldService.createHold}(Phase 1, 재고/슬롯 락)와
     * {@code paymentService.createPaymentIntent}(Phase 2, Stripe 호출)는 각자 독립된 트랜잭션을
     * 열어야 하는데, 이 메서드가 감싸는 트랜잭션을 갖고 있으면 둘 다 거기에 합류해버려서 재고/슬롯
     * 락이 Stripe 응답을 기다리는 동안까지 유지된다 — discussion#16이 버린 방안 1(Combined)과
     * 똑같아진다. 인증/금액한도 검증과 응답 조립은 순수 조회라 각 repository 호출이
     * (Spring Data JPA의 {@code SimpleJpaRepository} 기본 동작대로) 자체 트랜잭션으로 처리된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReservationDetailResponse createReservation(Long userId, ReservationCreateRequest request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));
        LocalDateTime now = LocalDateTime.now();

        // 금액 한도 검증 (#26) — createHold 전에 Currency/Rate를 미리 읽어 한도 확인 후 lockedRate 계산.
        // 유저 단위 불변식(노쇼/동시 PENDING_PAYMENT 제한)은 여기서 확인하지 않는다 — Phase 1
        // (createHold)이 유저 락을 잡은 채로 확인해야 TOCTOU가 안 생긴다(위 문서 방안 1).
        Branch branchForValidation = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BRANCH_NOT_FOUND));
        Currency currency = currencyRepository.findByCode(request.currencyCode())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.CURRENCY_NOT_FOUND));
        BranchCurrencyRate rateForValidation = branchCurrencyRateRepository
                .findRate(branchForValidation.getId(), request.currencyCode())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.STOCK_EXCEEDED));
        double lockedRate = assertAmountWithinLimits(currency, rateForValidation, request.amount());

        Reservation reservation = reservationHoldService.createHold(userId, request, now, lockedRate);

        PaymentIntentResult intent;
        try {
            intent = paymentService.createPaymentIntent(reservation);
        } catch (BusinessException e) {
            if (e.getErrorCode() == BusinessErrorCode.PAYMENT_INTENT_CREATE_FAILED) {
                // Stripe가 요청을 명확히 거절해 PaymentIntent가 생성되지 않았음이 확인된 경우만
                // 즉시 보상한다 — expireOnePendingPayment는 ReservationExpirySweeper가 5분 TTL
                // 만료 건마다 호출하는 것과 같은 메서드라, "그 정리를 기다리지 않고 지금 바로
                // 한다"는 것과 같다. 상태 가드가 있어 멱등이지만, 그 사이 스윕러가 먼저 같은 행을
                // 만졌다면 낙관적 락 충돌이 날 수 있어 원래 예외(e)를 가리지 않도록 감싼다.
                try {
                    self.expireOnePendingPayment(reservation.getId());
                } catch (ObjectOptimisticLockingFailureException lockConflict) {
                    log.info("[ReservationService] Stripe 실패 보상 중 낙관적 락 충돌 — 이미 다른 경로(스윕러 등)로 "
                            + "정리된 것으로 판단, reservationId={}", reservation.getId());
                }
            }
            // PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN(타임아웃/Stripe 5xx/idempotency 충돌)은 여기서
            // 보상하지 않는다 — Stripe가 실제로는 PaymentIntent를 만들었을 수 있어, 지금 취소하면
            // 그쪽엔 살아있는 intent가 있는데 우리 예약만 취소된 상태로 어긋날 수 있다. 이 경우는
            // ReservationExpirySweeper가 5분 결제 TTL 만료 시 findOverduePendingPaymentIds() +
            // expireOnePendingPayment()로 재고/슬롯을 회수한다.
            throw e;
        }

        // 결제 대기(PENDING_PAYMENT) 홀드 생성 알림 발송은 Notification 도메인이 없어 생략한다.
        // QR은 결제 승인 전에는 발급하지 않는다 — PaymentService.handlePaymentSucceeded(웹훅)에서 발급한다.

        Branch branch = getBranchOrThrow(reservation.getBranchId());
        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), reservation.getCurrencyCode())
                .orElse(null);
        return toDetail(reservation, branch, rate, intent.clientSecret());
    }

    public ReservationPageResponse listMyReservations(Long userId, String statusFilter, Pageable pageable) {
        Page<Reservation> reservations = statusFilter == null || statusFilter.isBlank()
                ? reservationRepository.findMyReservations(userId, pageable)
                : reservationRepository.findMyReservationsByStatus(userId, parseStatuses(statusFilter), pageable);

        return ReservationPageResponse.of(reservations.map(this::toSummary));
    }

    public ReservationDetailResponse getReservation(Long userId, Long id) {
        Reservation reservation = getReservationOrThrow(id);
        assertOwner(reservation, userId);

        Branch branch = getBranchOrThrow(reservation.getBranchId());
        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), reservation.getCurrencyCode())
                .orElse(null);
        return toDetail(reservation, branch, rate);
    }

    public void cancelReservation(Long userId, Long id) {
        try {
            self.doCancelReservation(userId, id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("[ReservationService] cancelReservation 낙관적 락 충돌 — reservationId={}, userId={}", id, userId);
            Reservation reservation = getReservationOrThrow(id);
            assertOwner(reservation, userId);
            if (reservation.getStatus() != ReservationStatus.CANCELLED) {
                throw new BusinessException(BusinessErrorCode.RESERVATION_UPDATE_CONFLICT);
            }
            // 이미 다른 요청(취소/만료 스윕)이 먼저 취소를 완료함 — 사용자 의도(취소)는 이미
            // 달성된 상태이므로 에러가 아니라 성공으로 처리한다 (discussion#41).
        }

        // 취소 알림 발송은 Notification 도메인이 없어 생략한다.
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doCancelReservation(Long userId, Long id) {
        Reservation reservation = getReservationOrThrow(id);
        assertOwner(reservation, userId);

        reservation.cancel(false);
        restoreStock(reservation);
        restoreTimeSlot(reservation);
    }

    /**
     * QR Scan 화면의 Reject 버튼 — 지점 창구가 예약을 거절한다. 소유자(owner) 검증 대신
     * 지점(branchId) 일치 검증으로 인가한다는 점만 cancelReservation과 다르다.
     */
    public void rejectByBranch(Long branchId, Long reservationId) {
        try {
            self.doRejectByBranch(branchId, reservationId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("[ReservationService] rejectByBranch 낙관적 락 충돌 — reservationId={}, branchId={}",
                    reservationId, branchId);
            Reservation reservation = getReservationOrThrow(reservationId);
            if (!reservation.getBranchId().equals(branchId)) {
                throw new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND);
            }
            if (reservation.getStatus() != ReservationStatus.CANCELLED) {
                throw new BusinessException(BusinessErrorCode.RESERVATION_UPDATE_CONFLICT);
            }
            // 이미 다른 요청이 먼저 거절/취소를 완료함 — 의도(거절)는 이미 달성됨, 성공 처리.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doRejectByBranch(Long branchId, Long reservationId) {
        Reservation reservation = getReservationOrThrow(reservationId);
        if (!reservation.getBranchId().equals(branchId)) {
            throw new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND);
        }
        reservation.cancel(false);
        restoreStock(reservation);
        restoreTimeSlot(reservation);
    }

    public RedeemResponse redeem(Long branchId, Long reservationId, RedeemRequest request) {
        try {
            return self.doRedeem(branchId, reservationId, request);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("[ReservationService] redeem 낙관적 락 충돌 — reservationId={}, branchId={}",
                    reservationId, branchId);
            Reservation reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND));
            if (!reservation.getBranchId().equals(branchId)) {
                throw new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND);
            }
            if (reservation.getStatus() != ReservationStatus.COMPLETED) {
                // 다른 요청이 그 사이 취소/만료시켜서 완료가 아닌 다른 상태로 넘어간 경우 —
                // 이건 "동시에 같은 픽업을 처리"한 게 아니라 다른 종류의 충돌이라 재시도 안내.
                throw new BusinessException(BusinessErrorCode.RESERVATION_UPDATE_CONFLICT);
            }
            // 이미 다른 요청(동시 스캔 등)이 먼저 완료 처리함 — 픽업 확인 의도는 이미 달성된
            // 상태이므로 에러가 아니라 그 결과를 그대로 응답한다 (discussion#41과 같은 원칙).
            Branch branch = getBranchOrThrow(reservation.getBranchId());
            BranchCurrencyRate rate = branchCurrencyRateRepository
                    .findRate(branch.getId(), reservation.getCurrencyCode())
                    .orElse(null);
            return RedeemResponse.of(reservation, toDetail(reservation, branch, rate));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RedeemResponse doRedeem(Long branchId, Long reservationId, RedeemRequest request) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.getBranchId().equals(branchId)) {
            throw new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        if (reservation.isExpired(now)) {
            // 여기서 취소/재고·슬롯 복원까지 하면, 뒤이은 QR_EXPIRED 예외(unchecked)가
            // 트랜잭션 전체를 롤백시켜 방금 한 복원 작업까지 함께 사라진다.
            // 실제 정리는 expireOverdueReservations() 스윕러가 전담하고, 여기서는 만료 여부만 판단해 응답한다.
            throw new BusinessException(BusinessErrorCode.QR_EXPIRED);
        }
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new BusinessException(BusinessErrorCode.ALREADY_COMPLETED);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException(BusinessErrorCode.QR_ALREADY_USED);
        }
        if (!Boolean.TRUE.equals(request.idVerified())) {
            throw new BusinessException(BusinessErrorCode.IDENTITY_MISMATCH);
        }
        if (reservation.getQrToken() == null || !constantTimeEquals(reservation.getQrToken(), request.qrToken())) {
            throw new BusinessException(BusinessErrorCode.QR_ALREADY_USED);
        }

        reservation.complete(now);

        Branch branch = getBranchOrThrow(reservation.getBranchId());
        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), reservation.getCurrencyCode())
                .orElse(null);
        return RedeemResponse.of(reservation, toDetail(reservation, branch, rate));
    }

    /**
     * discussion#41 방안 2 — 스윕은 여러 예약을 한 배치로 처리하는데, 한 건이 낙관적 락
     * 충돌(다른 요청과 동시 처리)이 나도 나머지 예약 정리까지 같이 롤백되면 안 된다. 그래서
     * 예약 ID 목록만 여기서 돌려주고, 실제 건별 처리(REQUIRES_NEW)와 충돌 캐치는
     * {@link ReservationExpirySweeper}가 건 단위로 호출하며 담당한다.
     */
    public List<Long> findOverdueReservationIds() {
        LocalDateTime now = LocalDateTime.now();
        return reservationRepository.findExpiredReservations(ReservationStatus.RESERVED, now)
                .stream().map(Reservation::getId).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOneOverdueReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.RESERVED) {
            return; // 이미 다른 경로(취소 등)에서 처리됨 — 멱등 no-op
        }
        reservation.cancel(true);
        restoreStock(reservation);
        restoreTimeSlot(reservation);
    }

    /**
     * discussion#16: 결제 TTL(5분)을 넘긴 유령 홀드(PENDING_PAYMENT)를 EXPIRED로 풀고 재고/슬롯을
     * 복원한다. 노쇼 이력(autoExpired)과는 분리한다 — 결제까지 가지 않은 홀드는 노쇼가 아니다.
     * discussion#41 방안 2 — 위 expireOneOverdueReservation과 같은 이유로 건별 조회/처리만
     * 담당하고, 배치 순회와 충돌 캐치는 {@link ReservationExpirySweeper}가 한다.
     */
    public List<Long> findOverduePendingPaymentIds() {
        LocalDateTime now = LocalDateTime.now();
        return reservationRepository.findExpiredPendingPayments(ReservationStatus.PENDING_PAYMENT, now)
                .stream().map(Reservation::getId).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOnePendingPayment(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            return; // 이미 다른 경로에서 처리됨 — 멱등 no-op
        }
        reservation.expireHold();
        restoreStock(reservation);
        restoreTimeSlot(reservation);
    }

    private void restoreStock(Reservation reservation) {
        branchCurrencyRateRepository.findForUpdate(reservation.getBranchId(), reservation.getCurrencyCode())
                .ifPresent(rate -> rate.increaseStock(reservation.getAmount()));
    }

    private void restoreTimeSlot(Reservation reservation) {
        branchTimeSlotRepository
                .lockForUpdate(reservation.getBranchId(), reservation.getPickupDate(), reservation.getPickupTime())
                .ifPresent(BranchTimeSlot::increaseRemaining);
    }

    /**
     * PRD §19.1, §21.2: USD 10,000 상당액 이상은 차단하고, 10,000 VND 상당액 미만도 차단한다.
     * 세 통화(요청 통화/USD/VND) 모두 KRW 기준 환율(Currency.sellRate)로 환산해 비교한다 —
     * 한도는 지점 우대율과 무관한 시장 기준액이라 지점별 finalRate가 아닌 Currency의 기준
     * sellRate를 쓴다. 반환값은 이 지점의 finalRate로, 예약에 그대로 lockedRate로 고정된다.
     */
    private double assertAmountWithinLimits(Currency currency, BranchCurrencyRate rate, double amount) {
        double amountKrw = currency.getSellRate() * amount;

        Currency usd = currency.getCode().equalsIgnoreCase("USD")
                ? currency : currencyRepository.findByCode("USD").orElse(null);
        if (usd != null && amountKrw >= usd.getSellRate() * AMOUNT_LIMIT_USD) {
            throw new BusinessException(BusinessErrorCode.AMOUNT_LIMIT_EXCEEDED);
        }

        Currency vnd = currency.getCode().equalsIgnoreCase("VND")
                ? currency : currencyRepository.findByCode("VND").orElse(null);
        if (vnd != null && amountKrw < vnd.getSellRate() * AMOUNT_MIN_VND) {
            throw new BusinessException(BusinessErrorCode.AMOUNT_BELOW_MINIMUM);
        }

        return currency.calculateFinalRate(rate.getPreferentialRate());
    }

    private void assertOwner(Reservation reservation, Long userId) {
        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN);
        }
    }

    private Reservation getReservationOrThrow(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND));
    }

    private Branch getBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BRANCH_NOT_FOUND));
    }

    private ReservationSummaryResponse toSummary(Reservation reservation) {
        String branchName = branchRepository.findById(reservation.getBranchId())
                .map(Branch::getName)
                .orElse(null);
        return ReservationSummaryResponse.of(reservation, branchName);
    }

    private ReservationDetailResponse toDetail(Reservation reservation, Branch branch, BranchCurrencyRate rate) {
        return toDetail(reservation, branch, rate, null);
    }

    private ReservationDetailResponse toDetail(Reservation reservation, Branch branch, BranchCurrencyRate rate,
                                                String paymentClientSecret) {
        Double preferentialRate = rate != null ? rate.getPreferentialRate() : null;
        boolean reservationAvailable = rate != null && rate.hasStock();
        Double finalRate = rate != null
                ? currencyRepository.findByCode(reservation.getCurrencyCode())
                        .map(currency -> currency.calculateFinalRate(rate.getPreferentialRate()))
                        .orElse(null)
                : null;
        BranchSummaryResponse branchSummary = BranchSummaryResponse.of(branch, null,
                branch.isOpenNow(LocalDateTime.now()), preferentialRate, finalRate, reservationAvailable);
        return ReservationDetailResponse.of(reservation, branchSummary, paymentClientSecret);
    }

    private List<ReservationStatus> parseStatuses(String statusFilter) {
        try {
            return Arrays.stream(statusFilter.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(ReservationStatus::valueOf)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BusinessErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
