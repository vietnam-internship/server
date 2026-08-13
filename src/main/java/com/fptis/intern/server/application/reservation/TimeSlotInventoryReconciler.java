package com.fptis.intern.server.application.reservation;

import com.fptis.intern.server.domain.branch.BranchTimeSlot;
import com.fptis.intern.server.domain.branch.BranchTimeSlotRepository;
import com.fptis.intern.server.global.notify.DiscordNotifier;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * discussion-reservation-optimistic-lock-observability.md 방안 2 — 낙관적 락 충돌 처리 코드를
 * 전혀 재사용하지 않는 순수 조회로 remaining > capacity(있을 수 없는 상태, 이중 복원 의심)를
 * 찾는다. 방어 코드가 스스로를 검증하면 같은 실수를 반복할 뿐이라 별도 경로로 둔다.
 *
 * 이 위반은 동시 접속 트래픽이 아니라 사람이 만든 도구(복구 스크립트, 벌크 업데이트, DB 직접
 * 수정)가 원인이라 몇 분 안에 잡아야 할 이유가 없다 — ReservationExpirySweeper(60초 틱)에
 * 얹지 않고 하루 1회 별도 스케줄로 돌려 상시 전체 스캔 비용을 피한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeSlotInventoryReconciler {

    private final BranchTimeSlotRepository branchTimeSlotRepository;
    private final DiscordNotifier discordNotifier;

    @Scheduled(cron = "${travelx.reservation.inventory-reconcile-cron:0 0 3 * * *}")
    public void reconcileAndNotify() {
        try {
            reconcile();
        } catch (InventoryConsistencyViolationException e) {
            log.error("[TimeSlotInventoryReconciler] {}", e.getMessage());
            discordNotifier.send("[TravelX] " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    void reconcile() {
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
