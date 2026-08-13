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
import com.fptis.intern.server.domain.payment.PaymentRepository;
import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.config.ReservationTimingConfig;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.reservation.dto.ReservationCreateRequest;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * `ReservationServiceTest`와 다른 목적: 거기서는 서비스를 {@code new}로 직접 만들어 Spring 프록시가
 * 없으므로 {@code @Transactional}/{@code REQUIRES_NEW}/{@code NOT_SUPPORTED}가 전부 무력하다 —
 * "보상 로직이 도는가"만 검증할 수 있고 "프레임워크가 실제로 트랜잭션을 나눠서 커밋/롤백하는가"는
 * 검증하지 못한다. 여기서는 {@code @Import}로 서비스 빈을 스프링 컨테이너가 만들게 해서 진짜
 * 프록시를 통과시킨다.
 *
 * <p>클래스 레벨 {@code @Transactional(NOT_SUPPORTED)}로 {@code @DataJpaTest}의 기본 동작(테스트
 * 메서드 전체를 트랜잭션 하나로 감싸고 종료 시 롤백)을 명시적으로 끈다. 이걸 안 끄면 두 가지가
 * 깨진다 — ① {@code createReservation}(NOT_SUPPORTED)이 이 바깥 트랜잭션을 매번 일시 중단시켜야
 * 하는데 그 자체는 되지만, ② MySQL REPEATABLE READ에서 이 바깥 트랜잭션의 스냅샷이 테스트 설정
 * 시점에 이미 고정돼 있어서, 중단된 사이에 Phase 1/2가 커밋한 데이터를 재개 후 일반 SELECT로 다시
 * 읽으면 스냅샷이 낡아 안 보일 수 있다. 트랜잭션 자체를 아예 안 열면 이 문제가 없다 — 대신 자동
 * 롤백이 없으므로 각 테스트가 매번 새 유저/지점을 만들어 서로 겹치지 않게 한다.
 */
@Testcontainers
@DataJpaTest
@EnableJpaAuditing
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationHoldService.class, PaymentService.class, ReservationService.class,
        ReservationTimingConfig.class, ReservationTransactionBoundaryIT.ControllableGatewayConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservationTransactionBoundaryIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private PaymentRepository paymentRepository;
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
    private FailureSwitch failureSwitch;

    private User user;
    private Branch branch;

    /**
     * 실패 여부를 테스트마다 바꿔야 하는데 Spring 빈은 기본 싱글턴이라, 이 스위치를 통해
     * {@link ControllableGatewayConfig}가 만드는 {@link PaymentGateway}의 동작을 외부에서 조작한다.
     */
    static class FailureSwitch {
        private volatile BusinessErrorCode nextFailure;

        void failNextWith(BusinessErrorCode errorCode) {
            this.nextFailure = errorCode;
        }
    }

    @TestConfiguration
    static class ControllableGatewayConfig {
        @Bean
        FailureSwitch failureSwitch() {
            return new FailureSwitch();
        }

        @Bean
        PaymentGateway paymentGateway(FailureSwitch failureSwitch) {
            return (idempotencyKey, amountMinorUnits, currency, metadata) -> {
                BusinessErrorCode failure = failureSwitch.nextFailure;
                if (failure != null) {
                    throw new BusinessException(failure);
                }
                return new PaymentIntentResult("pi_" + idempotencyKey, "secret_" + idempotencyKey);
            };
        }
    }

    @BeforeEach
    void setUp() {
        failureSwitch.nextFailure = null;

        user = userRepository.save(User.builder()
                .name("tester-" + System.nanoTime())
                .email("tester-" + System.nanoTime() + "@example.com")
                .role(Role.USER)
                .build());
        ReflectionTestUtils.setField(user, "phoneVerified", true);
        userRepository.save(user);

        branch = branchRepository.save(Branch.builder()
                .name("경계테스트지점-" + System.nanoTime())
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

        Currency currency = currencyRepository.findByCode("USD")
                .orElseGet(() -> Currency.builder().code("USD").country("미국").buyRate(1370.0).sellRate(1400.0).build());
        currency.updateRates(1370.0, 1400.0);
        currencyRepository.save(currency);
    }

    private ReservationCreateRequest request(String pickupTime) {
        return new ReservationCreateRequest("USD", branch.getId(), 500, LocalDate.now().plusDays(1), pickupTime);
    }

    @Test
    void paymentRowIsRolledBackWhenGatewayDefinitelyFails() {
        failureSwitch.failNextWith(BusinessErrorCode.PAYMENT_INTENT_CREATE_FAILED);

        assertThatThrownBy(() -> reservationService.createReservation(user.getId(), request("13:00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.PAYMENT_INTENT_CREATE_FAILED);

        // Phase 2(createPaymentIntent, REQUIRES_NEW)가 Payment.initiate()+save() 이후 예외를 던졌을 때,
        // 그 트랜잭션 자체가 롤백돼 Payment row가 DB에 전혀 안 남아야 한다 — "우리가 insert를 안
        // 해서"가 아니라 프레임워크가 실제로 트랜잭션을 되돌렸다는 증거.
        Reservation reservation = reservationRepository.findMyReservations(user.getId(), PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(paymentRepository.findByReservationId(reservation.getId())).isEmpty();
    }

    @Test
    void holdStaysCommittedWhenPaymentOutcomeIsUnknown() {
        failureSwitch.failNextWith(BusinessErrorCode.PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN);

        assertThatThrownBy(() -> reservationService.createReservation(user.getId(), request("14:00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN);

        // Phase 1(createHold, REQUIRED — 이 시점엔 바깥 트랜잭션이 없으므로 사실상 자기 것으로 새로
        // 열림)은 Phase 2 실패와 무관하게 이미 커밋된 상태여야 한다. 이 read는 클래스 레벨
        // NOT_SUPPORTED 덕에 트랜잭션 없이(=매번 최신 커밋 데이터로) 실행되므로, REPEATABLE READ
        // 스냅샷이 낡아서 통과하는 게 아니라 진짜로 커밋된 걸 확인하는 것이다.
        Reservation reservation = reservationRepository.findMyReservations(user.getId(), PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);

        BranchCurrencyRate rate = branchCurrencyRateRepository.findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(500); // 차감된 채 복원 안 됨 — 보상 대상이 아님
    }
}
