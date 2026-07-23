package com.fptis.intern.server.application.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchTimeSlotRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ReservationService는 리포지토리에만 의존하므로 웹 계층 없이 @DataJpaTest 슬라이스에서
 * 직접 생성해 재고 차감/슬롯 정원/노쇼 제한 같은 핵심 비즈니스 규칙을 검증한다.
 *
 * ReservationService를 @Import로 빈 등록해 @Autowired로 주입받는다 — 유저 행 락(pessimistic lock)이
 * createReservation() 메서드 트랜잭션 범위 전체에서 유지되는지 검증하려면(동시 요청 테스트),
 * @Transactional AOP 프록시가 실제로 적용된 스프링 빈이어야 한다. 단순 new로 생성하면
 * 리포지토리 호출마다 트랜잭션이 쪼개져 락이 곧바로 풀려버려 레이스를 재현할 수 없다.
 */
@Testcontainers
@DataJpaTest
@EnableJpaAuditing
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReservationService.class)
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
    private ReservationService reservationService;

    private User verifiedUser;
    private Branch branch;

    @BeforeEach
    void setUp() {
        verifiedUser = userRepository.save(User.builder()
                .name("tester")
                .email("tester@example.com")
                .role(Role.USER)
                .build());

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

    @Test
    void createsReservationAndDecreasesStock() {
        ReservationDetailResponse response = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(response.qrPayload()).isNotBlank();
        assertThat(response.reservationNumber()).startsWith("TX-");

        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), "USD").orElseThrow();
        assertThat(rate.getReservationOnlyStock()).isEqualTo(500);
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

        assertThatThrownBy(() -> reservationService.createReservation(
                another.getId(), createRequest(LocalDate.now().plusDays(1), "10:30")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.TIME_SLOT_FULL);
    }

    /**
     * 노쇼 이력이 있는 유저가 서로 다른 두 스레드(=서로 다른 DB 커넥션/트랜잭션)에서 동시에
     * 예약을 시도해도 유저 행 락(findForUpdate)에 의해 직렬화되어 1건만 성공해야 한다.
     * 테스트 트랜잭션(@DataJpaTest 기본 롤백)을 NOT_SUPPORTED로 꺼서 setUp()에서 만든 데이터가
     * 즉시 커밋되게 하고, 각 스레드가 진짜 자신만의 트랜잭션/락을 얻도록 한다 — 그래야 이 테스트가
     * 실제 DB 락 경합을 재현한다. 이 때문에 테스트 종료 후 직접 데이터를 정리해 다음 테스트가
     * 깨끗한 상태에서 시작하도록 한다.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsFromNoShowUserOnlyOneSucceeds() throws Exception {
        try {
            ReservationDetailResponse noShowSource = reservationService.createReservation(
                    verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "09:00"));
            Reservation noShowReservation = reservationRepository.findById(noShowSource.id()).orElseThrow();
            ReflectionTestUtils.setField(noShowReservation, "expiresAt", LocalDateTime.now().minusMinutes(1));
            reservationRepository.save(noShowReservation);
            reservationService.expireOverdueReservations();

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<Future<BusinessErrorCode>> futures = new ArrayList<>();
            futures.add(executor.submit(createReservationTask(ready, start, "10:00")));
            futures.add(executor.submit(createReservationTask(ready, start, "10:30")));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<BusinessErrorCode> outcomes = new ArrayList<>();
            for (Future<BusinessErrorCode> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }
            executor.shutdown();

            assertThat(outcomes).containsExactlyInAnyOrder(null, BusinessErrorCode.NO_SHOW_LIMIT);

            long activeCount = reservationRepository.countActiveReservations(
                    verifiedUser.getId(), ReservationStatus.RESERVED, LocalDateTime.now());
            assertThat(activeCount).isEqualTo(1);
        } finally {
            reservationRepository.deleteAll();
            branchTimeSlotRepository.deleteAll();
            branchCurrencyRateRepository.deleteAll();
            branchRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    private Callable<BusinessErrorCode> createReservationTask(CountDownLatch ready, CountDownLatch start,
                                                                String pickupTime) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                reservationService.createReservation(verifiedUser.getId(),
                        createRequest(LocalDate.now().plusDays(1), pickupTime));
                return null;
            } catch (BusinessException e) {
                return (BusinessErrorCode) e.getErrorCode();
            }
        };
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

        var redeemed = reservationService.redeem(branch.getId(), created.id(),
                new RedeemRequest(created.qrPayload(), true));

        assertThat(redeemed.status()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(redeemed.pickedUpAt()).isNotNull();
    }

    @Test
    void redeemRejectsMismatchedQrToken() {
        ReservationDetailResponse created = reservationService.createReservation(
                verifiedUser.getId(), createRequest(LocalDate.now().plusDays(1), "10:30"));

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
}
