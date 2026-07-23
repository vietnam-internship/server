package com.fptis.intern.server.domain.branch;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchTimeSlotRepository extends JpaRepository<BranchTimeSlot, Long> {

    /**
     * 슬롯 행이 없으면 remaining=capacity로 생성하고, 이미 있으면 아무것도 바꾸지 않는다(no-op update).
     * MySQL의 INSERT ... ON DUPLICATE KEY UPDATE는 중복 시에도 해당 행에 배타 락을 잡으므로,
     * 같은 트랜잭션 안에서 뒤이은 {@link #lockForUpdate}가 추가 대기 없이 그 락을 그대로 이어받는다 —
     * "행이 없으면 동시에 두 트랜잭션이 각자 INSERT를 시도"하는 경쟁을 DB 레벨에서 원자적으로 막는다.
     */
    @Modifying
    @Query(value = "insert into branch_time_slots (branch_id, slot_date, slot_time, remaining, created_at, updated_at) "
            + "values (:branchId, :slotDate, :slotTime, :capacity, now(6), now(6)) "
            + "on duplicate key update id = id", nativeQuery = true)
    void ensureExists(@Param("branchId") Long branchId, @Param("slotDate") LocalDate slotDate,
                       @Param("slotTime") LocalTime slotTime, @Param("capacity") int capacity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BranchTimeSlot s where s.branchId = :branchId and s.slotDate = :slotDate and s.slotTime = :slotTime")
    Optional<BranchTimeSlot> lockForUpdate(@Param("branchId") Long branchId, @Param("slotDate") LocalDate slotDate,
                                            @Param("slotTime") LocalTime slotTime);
}
