package com.fptis.intern.server.domain.reservation;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("select r from Reservation r where r.userId = :userId")
    Page<Reservation> findMyReservations(@Param("userId") Long userId, Pageable pageable);

    @Query("select r from Reservation r where r.userId = :userId and r.status in :statuses")
    Page<Reservation> findMyReservationsByStatus(@Param("userId") Long userId,
                                                  @Param("statuses") List<ReservationStatus> statuses, Pageable pageable);

    @Query("select count(r) from Reservation r "
            + "where r.userId = :userId and r.status = :status and r.expiresAt > :now")
    long countActiveReservations(@Param("userId") Long userId, @Param("status") ReservationStatus status,
                                  @Param("now") LocalDateTime now);

    @Query("select count(r) from Reservation r "
            + "where r.userId = :userId and r.status = :status and r.autoExpired = true and r.updatedAt > :since")
    long countNoShowsSince(@Param("userId") Long userId, @Param("status") ReservationStatus status,
                            @Param("since") LocalDateTime since);

    @Query("select r from Reservation r "
            + "where r.userId = :userId and r.status = :status and r.autoExpired = true order by r.updatedAt desc")
    List<Reservation> findNoShows(@Param("userId") Long userId, @Param("status") ReservationStatus status, Pageable pageable);

    @Query("select r from Reservation r where r.status = :status and r.expiresAt < :now")
    List<Reservation> findExpiredReservations(@Param("status") ReservationStatus status, @Param("now") LocalDateTime now);
}
