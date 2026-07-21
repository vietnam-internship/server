package com.fptis.intern.server.domain.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    Page<Reservation> findByUserIdAndStatusIn(Long userId, List<ReservationStatus> statuses, Pageable pageable);

    long countByBranchIdAndPickupDateAndPickupTimeAndStatusAndExpiresAtAfter(
            Long branchId, LocalDate pickupDate, LocalTime pickupTime, ReservationStatus status, LocalDateTime now);

    long countByUserIdAndStatusAndExpiresAtAfter(Long userId, ReservationStatus status, LocalDateTime now);

    long countByUserIdAndStatusAndAutoExpiredTrueAndUpdatedAtAfter(
            Long userId, ReservationStatus status, LocalDateTime since);

    Optional<Reservation> findTopByUserIdAndStatusAndAutoExpiredTrueOrderByUpdatedAtDesc(
            Long userId, ReservationStatus status);

    List<Reservation> findAllByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime now);
}
