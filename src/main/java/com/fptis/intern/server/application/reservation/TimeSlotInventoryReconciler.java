package com.fptis.intern.server.application.reservation;

import com.fptis.intern.server.domain.branch.BranchTimeSlot;
import com.fptis.intern.server.domain.branch.BranchTimeSlotRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * discussion-reservation-optimistic-lock-observability.md 방안 2 — 낙관적 락 충돌 처리 코드를
 * 전혀 재사용하지 않는 순수 조회로 remaining > capacity(있을 수 없는 상태, 이중 복원 의심)를
 * 찾는다. 방어 코드가 스스로를 검증하면 같은 실수를 반복할 뿐이라 별도 경로로 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeSlotInventoryReconciler {

    private final BranchTimeSlotRepository branchTimeSlotRepository;

    @Transactional(readOnly = true)
    public void reconcile() {
        List<BranchTimeSlot> violations = branchTimeSlotRepository.findOverCapacitySlots(LocalDate.now());
        if (violations.isEmpty()) {
            return;
        }

        for (BranchTimeSlot slot : violations) {
            log.error("[TimeSlotInventoryReconciler] 재고 정합성 위반 — slotId={}, branchId={}, "
                    + "slotDate={}, slotTime={}, remaining={}", slot.getId(), slot.getBranchId(),
                    slot.getSlotDate(), slot.getSlotTime(), slot.getRemaining());
        }
        throw new InventoryConsistencyViolationException(violations.size());
    }
}
