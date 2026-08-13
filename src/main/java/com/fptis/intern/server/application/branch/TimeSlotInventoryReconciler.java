package com.fptis.intern.server.application.branch;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchTimeSlot;
import com.fptis.intern.server.domain.branch.BranchTimeSlotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * discussion#41 방안 2(낙관적 락)는 사용자 취소와 만료 스윕이 동시에 같은 예약을 건드려도 재고·슬롯이
 * 두 번 복원되지 않도록 구조적으로 막는다 — 뒤에 커밋하려는 트랜잭션은 항상
 * {@code ObjectOptimisticLockingFailureException}으로 실패하고 롤백되기 때문이다. 이 컴포넌트는 그
 * 전제가 실제로 지켜지고 있는지 사후에 검증하는 안전망이다: 비관적 락까지 걸어 모든 취소·수령·만료
 * 경로에 상시 블로킹 비용을 지불하는 대신, 낙관적 락을 우선 적용하고 "혹시 이 방어를 우회하는 코드가
 * 나중에 들어와도" 잡아낼 수 있도록 별도 배치로 정합성을 주기적으로 확인한다.
 *
 * <p>슬롯 하나의 잔여 정원(remaining)은 그 슬롯이 속한 지점의 {@code timeSlotCapacity}를 절대
 * 넘을 수 없다는 불변식을 검사한다 — 넘었다면 어딘가에서 좌석이 실제로 이중 복원됐다는 뜻이다.
 * 알림 인프라(Slack 등)가 이 프로젝트에는 없어, 운영이 즉시 확인할 수 있도록 ERROR 레벨 로그로
 * 남기고 {@link MeterRegistry}에도 위반 건수를 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeSlotInventoryReconciler {

    private final BranchTimeSlotRepository branchTimeSlotRepository;
    private final BranchRepository branchRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${travelx.reservation.inventory-reconcile-interval-ms:300000}")
    public void reconcile() {
        Map<Long, Integer> capacityByBranchId = branchRepository.findActiveBranches().stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getTimeSlotCapacity, (a, b) -> a));

        for (BranchTimeSlot slot : branchTimeSlotRepository.findAllBySlotDateGreaterThanEqual(LocalDate.now())) {
            Integer capacity = capacityByBranchId.get(slot.getBranchId());
            if (capacity == null) {
                continue; // 비활성화된 지점 — 이 슬롯은 더 이상 신규 예약 대상이 아니므로 검사 제외.
            }
            if (slot.getRemaining() > capacity) {
                meterRegistry.counter("reservation.inventory_violation", "type", "time_slot").increment();
                log.error("[TimeSlotInventoryReconciler] 슬롯 잔여 정원이 지점 정원을 초과함 — 좌석 이중 복원 "
                                + "의심, 수동 확인 필요. slotId={}, branchId={}, slotDate={}, slotTime={}, "
                                + "remaining={}, capacity={}",
                        slot.getId(), slot.getBranchId(), slot.getSlotDate(), slot.getSlotTime(),
                        slot.getRemaining(), capacity);
            }
        }
    }
}
