package com.fptis.intern.server.application.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fptis.intern.server.application.payment.PaymentGateway;
import com.fptis.intern.server.application.payment.PaymentIntentResult;
import com.fptis.intern.server.application.payment.PaymentService;
import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchTimeSlotRepository;
import com.fptis.intern.server.domain.payment.Payment;
import com.fptis.intern.server.domain.payment.PaymentRepository;
import com.fptis.intern.server.domain.payment.PaymentStatus;
import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.reservation.dto.RedeemRequest;
import com.fptis.intern.server.presentation.reservation.dto.ReservationCreateRequest;
import com.fptis.intern.server.presentation.reservation.dto.ReservationDetailResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ReservationService/ReservationHoldService/PaymentService는 리포지토리에만 의존하므로 웹 계층
 * 없이 @DataJpaTest 슬라이스에서 직접 생성해 재고 차감/슬롯 정원/노쇼 제한/결제(Hold→Pay, Stripe
 * PaymentIntent+웹훅) 흐름 같은 핵심 비즈니스 규칙을 검증한다. 실제 Stripe SDK 대신
 * {@link FakePaymentGateway}로 PaymentGateway 포트를 대체한다.
 */
@Testcontainers
@DataJpaTest
@EnableJpaAuditing
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private BranchCurrencyRateRepository branchCurrencyRateRepository;
    @Autowired
    private BranchTimeSlotRepository branchTimeSlotRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    private ReservationService reservationService;
    private PaymentService paymentService;
    private User verifiedUser;
    private Branch branch;

    private static class FakePaymentGateway implements PaymentGateway {
        @Override
        public PaymentIntentResult createIntent(String idempotencyKey, long amountMinorUnits, String currency,
                                                 Map<String, String> metadata) {
            return new PaymentIntentResult("pi_" + idempotencyKey, "secret_" + idempotencyKey);
        }
    }

    @BeforeEach
    void setUp() {
        ReservationHoldService reservationHoldService = new ReservationHoldService(reservationRepository,
                branchRepository, branchCurrencyRateRepository, branchTimeSlotRepository);
        paymentService = new PaymentService(reservationRepository, paymentRepository, new FakePaymentGateway());
        reservationService = new ReservationService(reservationRepository, userRepository, branchRepository,
                branchCurrencyRateRepository, branchTimeSlotRepository, reservationHoldService, paymentService);

        verifiedUser = userRepository.save(User.builder()
                .name("tester")
                .email("tester@example.com")
                .role(Role.USER)
                .build());
        verifyPhone(verifiedUser);

        branch = branchRepository.save(Branch.builder()
                .name("명동 환전센터")
                .address("서울 중구 명동길 1")
                .latitude(37.5665)
                .longitude(126.9780)
                .phone("02-123-4567")
                .businessHours("평일 09:00-18:00")
                .timeSlotCapacity(1)
                .build());

        branchCurrencyRateRepository.save(BranchCurrencyRate.builder()
                .branchId(branch.getId())
                .currencyCode("USD")
                .preferentialRate(0.5)
                .reservationOnlyStock(1000)
                .build());
    }

    private ReservationCreateRequest createRequest(LocalDate pickupDate, String pickupTime) {
        return createRequest(pickupDate, pickupTime, 500);
    }

    private ReservationCreateRequest createRequest(LocalDate pickupDate, String pickupTime, double amount) {
        return new ReservationCreateRequest("USD", branch.getId(), amount, pickupDate, pickupTime);
    }

    /**
     * /auth/verify-phone(SMS 인증)은 아직 미구현이라 User에 정식 verifyPhone() 메서드가 없다 —
     * 테스트에서는 리플렉션으로 phoneVerified만 직접 세팅한다.
     */
    private void verifyPhone(User user) {
        ReflectionTestUtils.setField(user, "phoneVerified", true);
        userRepository.save(user);
    }

    /** 실제로는 Stripe 웹훅(payment_intent.succeeded)이 호출하는 경로를 그대로 흉내낸다. */
    private ReservationDetailResponse confirmPayment(Long userId, Long reservationId) {
        Payment payment = paymentRepository.findByReservationId(reservationId).orElseThrow();
        paymentService.handlePaymentSucceeded(payment.getPgPaymentIntentId());
        return reservationService.getReservation(userId, reservationId);
    }

    @Test
    void createsReservationAsPendingPaymentAndDecreasesStock() {
        ReservationDetailResponse response = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(response.qrPayload()).isNull();
        assertThat(response.paymentExpiresAt()).isNotNull();
        assertThat(response.paymentClientSecret()).isNotBlank();
        assertThat(response.reservationNumber()).startsWith("TX-");

        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(500);

        Payment payment = paymentRepository.findByReservationId(response.id()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPgPaymentIntentId()).isNotBlank();
    }

    @Test
    void getReservationDoesNotLeakPaymentClientSecretAfterCreation() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

        ReservationDetailResponse fetched = reservationService.getReservation(verifiedUser.getId(), created.id());

        assertThat(fetched.paymentClientSecret()).isNull();
    }

    @Test
    void rejectsWhenStockExceeded() {
        assertThatThrownBy(() -> reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30", 2000)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.STOCK_EXCEEDED);
    }

    @Test
    void rejectsWhenTimeSlotFull() {
        reservationService.createReservation(verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

        User another = userRepository.save(User.builder()
                .name("another")
                .email("another@example.com")
                .role(Role.USER)
                .build());
        verifyPhone(another);

        assertThatThrownBy(() -> reservationService.createReservation(
                another.getId(), createRequest(LocalDate.now().plusDays(1), "10:30")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.TIME_SLOT_FULL);
    }

    @Test
    void rejectsConcurrentPendingPayment() {
        reservationService.createReservation(verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

        assertThatThrownBy(() -> reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(2), "10:30")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.CONCURRENT_PENDING_PAYMENT_LIMIT);
    }

    @Test
    void paymentSucceededWebhookIssuesQrAndStartsPickupWindow() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

        ReservationDetailResponse confirmed = confirmPayment(verifiedUser.getId(), created.id());

        assertThat(confirmed.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(confirmed.qrPayload()).isNotBlank();
        assertThat(confirmed.expiresAt()).isNotNull();

        Payment payment = paymentRepository.findByReservationId(created.id()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void paymentSucceededWebhookIsIdempotentOnRedelivery() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));
        confirmPayment(verifiedUser.getId(), created.id());

        // Stripe가 같은 이벤트를 재전송해도 예외 없이 그대로 유지돼야 한다.
        Payment payment = paymentRepository.findByReservationId(created.id()).orElseThrow();
        paymentService.handlePaymentSucceeded(payment.getPgPaymentIntentId());

        Reservation reservation = reservationRepository.findById(created.id()).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void paymentFailedWebhookKeepsHoldPendingForRetry() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));
        Payment payment = paymentRepository.findByReservationId(created.id()).orElseThrow();

        paymentService.handlePaymentFailed(payment.getPgPaymentIntentId());

        Reservation reservation = reservationRepository.findById(created.id()).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);

        Payment failedPayment = paymentRepository.findByReservationId(created.id()).orElseThrow();
        assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void paymentSucceededWebhookAfterHoldExpiredFlagsWithoutResurrectingReservation() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

        Reservation reservation = reservationRepository.findById(created.id()).orElseThrow();
        ReflectionTestUtils.setField(reservation, "paymentExpiresAt", LocalDateTime.now().minusMinutes(1));
        reservationRepository.save(reservation);
        reservationService.expireOverduePendingPayments();

        Payment payment = paymentRepository.findByReservationId(created.id()).orElseThrow();
        paymentService.handlePaymentSucceeded(payment.getPgPaymentIntentId());

        Reservation afterWebhook = reservationRepository.findById(created.id()).orElseThrow();
        assertThat(afterWebhook.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        Payment approvedButOrphaned = paymentRepository.findByReservationId(created.id()).orElseThrow();
        assertThat(approvedButOrphaned.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void cancelRestoresStockAndClearsQrToken() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

        reservationService.cancelReservation(verifiedUser.getId(), created.id());

        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(1000);

        Reservation reservation = reservationRepository.findById(created.id()).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getQrToken()).isNull();
    }

    @Test
    void redeemCompletesReservationWithMatchingQrToken() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));
        ReservationDetailResponse confirmed = confirmPayment(verifiedUser.getId(), created.id());

        var redeemed = reservationService.redeem(branch.getId(), created.id(),
                new RedeemRequest(confirmed.qrPayload(), true));

        assertThat(redeemed.status()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(redeemed.pickedUpAt()).isNotNull();
    }

    @Test
    void redeemRejectsMismatchedQrToken() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));
        confirmPayment(verifiedUser.getId(), created.id());

        assertThatThrownBy(() -> reservationService.redeem(branch.getId(), created.id(),
                new RedeemRequest("wrong-token", true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.QR_ALREADY_USED);
    }

    @Test
    void expireOverdueReservationsCancelsAndRestoresStock() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));
        confirmPayment(verifiedUser.getId(), created.id());

        Reservation reservation = reservationRepository.findById(created.id()).orElseThrow();
        ReflectionTestUtils.setField(reservation, "expiresAt", LocalDateTime.now().minusMinutes(1));
        reservationRepository.save(reservation);

        reservationService.expireOverdueReservations();

        Reservation expired = reservationRepository.findById(created.id()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(expired.isAutoExpired()).isTrue();

        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(1000);
    }

    @Test
    void expireOverduePendingPaymentsExpiresGhostHoldAndRestoresStock() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

        Reservation reservation = reservationRepository.findById(created.id()).orElseThrow();
        ReflectionTestUtils.setField(reservation, "paymentExpiresAt", LocalDateTime.now().minusMinutes(1));
        reservationRepository.save(reservation);

        reservationService.expireOverduePendingPayments();

        Reservation expired = reservationRepository.findById(created.id()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(expired.isAutoExpired()).isFalse();

        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(1000);
    }
}
