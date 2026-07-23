package com.fptis.intern.server.domain.reservation;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * MySQL REPEATABLE READ에서는 일반 SELECT(countActiveReservations 등)가 트랜잭션 시작 시점의
     * 스냅샷을 읽기 때문에, 유저 행 락(UserRepository#findForUpdate)으로 대기했다가 풀려나도
     * 그 사이 커밋된 다른 트랜잭션의 예약이 안 보일 수 있다. 락 획득 읽기(locking read)는 항상
     * 최신 커밋 데이터를 읽으므로, 노쇼 유저의 활성 예약 존재 여부는 반드시 이 메서드로 확인한다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from Reservation r "
            + "where r.userId = :userId and r.status = :status and r.expiresAt > :now")
    List<Reservation> findActiveReservationsForUpdate(@Param("userId") Long userId, @Param("status") ReservationStatus status,
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
