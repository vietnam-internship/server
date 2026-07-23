package com.fptis.intern.server.domain.branch;

import jakarta.persistence.*;
import com.fptis.intern.server.global.base.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "branch_time_slot_overrides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchTimeSlotOverride extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "capacity_limit", nullable = false)
    private Integer capacityLimit;

    @Column(name = "is_blocked", nullable = false)
    private Boolean isBlocked;

    @Builder
    private BranchTimeSlotOverride(Long branchId, LocalDate targetDate, LocalTime startTime, LocalTime endTime, Integer capacityLimit, Boolean isBlocked) {
        this.branchId = branchId;
        this.targetDate = targetDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacityLimit = capacityLimit;
        this.isBlocked = isBlocked;
    }
}
