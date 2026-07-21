package com.fptis.intern.server.domain.branch;

import com.fptis.intern.server.global.base.BaseTimeEntity;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지점의 30분 픽업 슬롯 하나(branchId, slotDate, slotTime)당 남은 예약 정원.
 * 동시성 처리는 discussions#13에서 채택한 "방안 A(1 Row Locked)"를 따른다 — 이 행 하나에
 * 비관적 락을 걸고 remaining을 확인·차감한 뒤에만 Reservation을 insert한다. 행은 해당 슬롯에
 * 첫 예약 시도가 들어올 때 지연 생성된다(사전 배치 없음) — 생성 이후 지점의
 * timeSlotCapacity가 바뀌어도 이미 만들어진 행의 remaining은 재조정되지 않는다.
 */
@Getter
@Entity
@Table(name = "branch_time_slots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchTimeSlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    @Column(nullable = false)
    private int remaining;

    @Builder
    private BranchTimeSlot(Long branchId, LocalDate slotDate, LocalTime slotTime, int remaining) {
        this.branchId = branchId;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
        this.remaining = remaining;
    }

    public void decreaseRemaining() {
        if (remaining <= 0) {
            throw new BusinessException(BusinessErrorCode.TIME_SLOT_FULL);
        }
        remaining--;
    }

    public void increaseRemaining() {
        remaining++;
    }
}
