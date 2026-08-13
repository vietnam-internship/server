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
import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.domain.payment.Payment;
import com.fptis.intern.server.domain.payment.PaymentRepository;
import com.fptis.intern.server.domain.payment.PaymentStatus;
import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.global.config.ReservationTimingProperties;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.reservation.dto.RedeemRequest;
import com.fptis.intern.server.presentation.reservation.dto.ReservationCreateRequest;
import com.fptis.intern.server.presentation.reservation.dto.ReservationDetailResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    private CurrencyRepository currencyRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @PersistenceContext
    private EntityManager entityManager;

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

    /**
     * discussion-reservation-payment-failure-compensation.md 검증용 — 지정한 에러코드로 항상
     * 실패하는 게이트웨이. PAYMENT_INTENT_CREATE_FAILED(확정 실패)/PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN
     * (결과 불확실) 각각에서 보상 여부가 갈리는지를 검증한다.
     */
    private static class FailingPaymentGateway implements PaymentGateway {
        private final BusinessErrorCode errorCode;

        private FailingPaymentGateway(BusinessErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        @Override
        public PaymentIntentResult createIntent(String idempotencyKey, long amountMinorUnits, String currency,
                                                 Map<String, String> metadata) {
            throw new BusinessException(errorCode);
        }
    }

    /** FakePaymentGateway 대신 지정한 PaymentGateway로 새 ReservationService 인스턴스를 만든다. */
    private ReservationService buildReservationService(PaymentGateway gateway) {
        ReservationTimingProperties timingProperties = new ReservationTimingProperties(5, 2, 5);
        ReservationHoldService holdService = new ReservationHoldService(reservationRepository, userRepository,
                branchRepository, branchCurrencyRateRepository, branchTimeSlotRepository, timingProperties);
        PaymentService gatewayPaymentService = new PaymentService(reservationRepository, paymentRepository, gateway,
                timingProperties);
        ReservationService service = new ReservationService(reservationRepository, userRepository, branchRepository,
                branchCurrencyRateRepository, branchTimeSlotRepository, holdService, gatewayPaymentService,
                currencyRepository, null);
        ReflectionTestUtils.setField(service, "self", service);
        return service;
    }

    @BeforeEach
    void setUp() {
        ReservationTimingProperties timingProperties = new ReservationTimingProperties(5, 2, 5);
        ReservationHoldService reservationHoldService = new ReservationHoldService(reservationRepository,
                userRepository, branchRepository, branchCurrencyRateRepository, branchTimeSlotRepository,
                timingProperties);
        paymentService = new PaymentService(reservationRepository, paymentRepository, new FakePaymentGateway(),
                timingProperties);
        reservationService = new ReservationService(reservationRepository, userRepository, branchRepository,
                branchCurrencyRateRepository, branchTimeSlotRepository, reservationHoldService, paymentService,
                currencyRepository, null);
        // self는 프록시(REQUIRES_NEW 등 트랜잭션 어드바이스)를 타야 하는 필드라 Spring 컨테이너
        // 밖에서 직접 생성할 때는 자기 자신을 그대로 넣는다 — 이 테스트 슬라이스에선 실제
        // REQUIRES_NEW 전파는 검증하지 않고, 낙관적 락 충돌 캐치/멱등 처리 로직만 검증한다.
        ReflectionTestUtils.setField(reservationService, "self", reservationService);

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

        saveCurrency("USD", "미국", 1370.0, 1400.0);
    }

    /**
     * V14 시드 마이그레이션이 USD 등 일부 통화를 이미 넣어두므로, 존재하면 테스트가 기대하는
     * 환율로 갱신하고 없으면 새로 만든다 — 시드 데이터 유무와 무관하게 결정적으로 동작해야 한다.
     */
    private Currency saveCurrency(String code, String country, double buyRate, double sellRate) {
        Currency currency = currencyRepository.findByCode(code)
                .orElseGet(() -> Currency.builder().code(code).country(country).buyRate(buyRate).sellRate(sellRate).build());
        currency.updateRates(buyRate, sellRate);
        return currencyRepository.save(currency);
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
        // preferentialRate 0.5, sellRate 1400 -> finalRate = 1400 * (1 - 0.005) = 1393.0
        assertThat(response.lockedRate()).isEqualTo(1393.0);
        assertThat(response.amountFrom()).isEqualTo(500 * 1393.0);

        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(500);

        Payment payment = paymentRepository.findByReservationId(response.id()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPgPaymentIntentId()).isNotBlank();
    }

    @Test
    void createReservationCompensatesHoldOnDefinitePaymentFailure() {
        ReservationService failingService = buildReservationService(
                new FailingPaymentGateway(BusinessErrorCode.PAYMENT_INTENT_CREATE_FAILED));
        LocalDate pickupDate = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> failingService.createReservation(verifiedUser.getId(), createRequest(pickupDate, "13:00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.PAYMENT_INTENT_CREATE_FAILED);

        Reservation failed = reservationRepository.findMyReservations(verifiedUser.getId(), PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(failed.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        // 보상으로 재고가 즉시 복원돼, 스윕러의 5분 TTL을 기다리지 않고 바로 재예약할 수 있어야 한다.
        ReservationDetailResponse retry = reservationService.createReservation(
                verifiedUser.getId(), createRequest(pickupDate, "13:00"));
        assertThat(retry.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);

        BranchCurrencyRate rate = branchCurrencyRateRepository.findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(500); // 1000 -> (실패분 복원) 1000 -> (재시도분 차감) 500
    }

    @Test
    void createReservationDoesNotCompensateOnAmbiguousPaymentFailure() {
        ReservationService failingService = buildReservationService(
                new FailingPaymentGateway(BusinessErrorCode.PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN));
        LocalDate pickupDate = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> failingService.createReservation(verifiedUser.getId(), createRequest(pickupDate, "14:00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN);

        // 결과 불확실 실패는 보상하지 않는다 — Stripe에 실제로 PaymentIntent가 생겼을 수 있어,
        // 예약을 PENDING_PAYMENT 그대로 두고 재고도 차감된 채로 남겨 TTL 스윕에 맡긴다.
        Reservation stillPending = reservationRepository.findMyReservations(verifiedUser.getId(), PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(stillPending.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);

        BranchCurrencyRate rate = branchCurrencyRateRepository.findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(500); // 1000 -> (차감된 채 복원 안 됨) 500

        // 보상이 없었으므로 동시 PENDING_PAYMENT 제한에 걸려 즉시 재시도도 못 한다.
        assertThatThrownBy(() -> reservationService.createReservation(verifiedUser.getId(), createRequest(pickupDate, "14:00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.CONCURRENT_PENDING_PAYMENT_LIMIT);
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
    void rejectsWhenAmountReachesUsdLimit() {
        // sellRate 1400 * 10,000 USD = 14,000,000 KRW, 요청 통화도 USD라 amountKrw와 같은 기준
        assertThatThrownBy(() -> reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30", 10_000)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.AMOUNT_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsWhenAmountBelowVndMinimum() {
        saveCurrency("VND", "베트남", 0.053, 0.055);
        // VND 10,000 * 0.055 = 550 KRW 미만이어야 하므로, USD 0.1 * 1400 = 140 KRW로 요청한다.
        assertThatThrownBy(() -> reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30", 0.1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.AMOUNT_BELOW_MINIMUM);
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
        reservationService.expireOnePendingPayment(created.id());

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
        // qrPayload는 "{branchId}:{reservationId}:{token}" 형식이라 실제 토큰만 뽑아 써야 한다.
        String qrToken = confirmed.qrPayload().split(":", 3)[2];

        var redeemed = reservationService.redeem(branch.getId(), created.id(),
                new RedeemRequest(qrToken, true));

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

        reservationService.expireOneOverdueReservation(created.id());

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

        reservationService.expireOnePendingPayment(created.id());

        Reservation expired = reservationRepository.findById(created.id()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(expired.isAutoExpired()).isFalse();

        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(1000);
    }

    /**
     * discussion#41 방안 2 검증 — 다른 트랜잭션이 이미 커밋해서 버전이 하나 올라간 뒤에도,
     * 그 커밋 전 버전을 든 채로 저장하려 하면 {@link ObjectOptimisticLockingFailureException}로
     * 막혀야 한다. @Version 없이는 이 저장이 조용히 성공해서 재고가 중복 복원된다(실제
     * 부하테스트로 재현: 동시 취소 10건 중 5건 성공, 재고 400 초과 복원).
     */
    @Test
    void staleVersionOnConcurrentCancelIsRejectedInsteadOfDoubleRestoringStock() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));
        Long id = created.id();

        // A(실제 서비스 경로)가 먼저 취소를 완료한다 — 버전이 하나 올라가고 재고가 복원된다.
        reservationService.cancelReservation(verifiedUser.getId(), id);
        BranchCurrencyRate rateAfterA = branchCurrencyRateRepository.findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rateAfterA.getReservationOnlyStock()).isEqualTo(1000);

        // B가 A보다 먼저(A 커밋 전) 이 예약을 읽어뒀던 상황을 흉내낸다 — 지금 실제 행 버전에서
        // 하나 뺀 "오래된" 버전을 들고, 상태도 A가 취소하기 전(RESERVED)으로 되돌린 detached
        // 사본을 만든다.
        Reservation staleCopy = reservationRepository.findById(id).orElseThrow();
        entityManager.detach(staleCopy);
        Long currentVersion = (Long) ReflectionTestUtils.getField(staleCopy, "version");
        ReflectionTestUtils.setField(staleCopy, "version", currentVersion - 1);
        ReflectionTestUtils.setField(staleCopy, "status", ReservationStatus.RESERVED);

        // B가 뒤늦게 자기 몫의 취소를 저장하려 하면, 실제 DB 버전과 안 맞아 충돌해야 한다 —
        // 그 결과로 재고가 또 복원되는 일이 없어야 한다(안 그러면 두 번 복원돼 1500이 됐을 것).
        staleCopy.cancel(false);
        assertThatThrownBy(() -> reservationRepository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        BranchCurrencyRate finalRate = branchCurrencyRateRepository.findRate(branch.getId(), "USD").orElseThrow();
        assertThat(finalRate.getReservationOnlyStock()).isEqualTo(1000);
    }
}
