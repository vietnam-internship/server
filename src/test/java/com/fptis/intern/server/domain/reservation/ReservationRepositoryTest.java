package com.fptis.intern.server.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @DataJpaTest + 실 MySQL 컨테이너로 Flyway V7 마이그레이션이 Reservation 엔티티와
 * 어긋나지 않는지(ddl-auto=validate) 검증한다.
 */
@Testcontainers
@DataJpaTest
@EnableJpaAuditing
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Test
    void savesReservationAndFindsByOwner() {
        User user = userRepository.save(User.builder()
                .name("tester")
                .email("tester@example.com")
                .role(Role.USER)
                .build());
        Branch branch = branchRepository.save(Branch.builder()
                .name("명동 환전센터")
                .address("서울 중구 명동길 1")
                .latitude(37.5665)
                .longitude(126.9780)
                .phone("02-123-4567")
                .businessHours("평일 09:00-18:00")
                .timeSlotCapacity(4)
                .build());

        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .userId(user.getId())
                .branchId(branch.getId())
                .currencyCode("USD")
                .amount(500)
                .pickupDate(LocalDate.now().plusDays(1))
                .pickupTime(LocalTime.of(10, 30))
                .now(now)
                .build());
        reservation.assignReservationNumber("TX-20260721-0001");
        reservationRepository.save(reservation);

        assertThat(reservationRepository.findMyReservations(user.getId(), PageRequest.of(0, 20)).getContent())
                .extracting(Reservation::getReservationNumber)
                .containsExactly("TX-20260721-0001");

        assertThat(reservationRepository.findMyReservationsByStatus(
                user.getId(), List.of(ReservationStatus.PENDING_PAYMENT), PageRequest.of(0, 20)).getContent())
                .hasSize(1);

        assertThat(reservationRepository.countByUserIdAndStatus(user.getId(), ReservationStatus.PENDING_PAYMENT))
                .isEqualTo(1);

        assertThat(reservationRepository.findExpiredReservations(ReservationStatus.RESERVED, now.minusHours(3)))
                .isEmpty();
        assertThat(reservationRepository.findExpiredPendingPayments(ReservationStatus.PENDING_PAYMENT, now.minusMinutes(10)))
                .isEmpty();

        reservation.confirmPayment(now);
        reservation.issueQrToken("raw-qr-token");
        reservationRepository.save(reservation);

        assertThat(reservationRepository.findMyReservationsByStatus(
                user.getId(), List.of(ReservationStatus.RESERVED), PageRequest.of(0, 20)).getContent())
                .hasSize(1);
    }
}
