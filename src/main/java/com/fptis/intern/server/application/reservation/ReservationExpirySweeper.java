package com.fptis.intern.server.application.reservation;

import com.fptis.intern.server.application.branch.TimeSlotInventoryReconciler;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 두 종류의 만료를 주기적으로 정리한다:
 * 1) 픽업 홀드(2시간) 만료된 RESERVED 예약 → CANCELLED(autoExpired=true, 노쇼), 재고 복원 (PRD §19.1)
 * 2) 결제 홀드(5분) 만료된 PENDING_PAYMENT 예약 → EXPIRED(유령 홀드), 재고 복원 (discussion#16)
 *
 * discussion#41 방안 2 — 예약 건별로 별도 트랜잭션(REQUIRES_NEW)에서 처리하고, 사용자의 취소
 * 요청 등과 낙관적 락이 충돌한 건은 개별로 건너뛴다. 한 건의 충돌이 같은 배치의 나머지 예약
 * 정리를 막으면 안 되기 때문이다.
 *
 * <p>{@link TimeSlotInventoryReconciler}의 정합성 검사도 이 틱 안에서 몇 번에 한 번씩 함께 호출한다 —
 * 이 프로젝트는 {@code @Scheduled} 전용 스레드 풀을 따로 구성하지 않아 기본값(스레드 1개)을 쓰는데,
 * 그 상태에서 별도 {@code @Scheduled} 메서드를 하나 더 만들면 이 스윕과 같은 스레드를 다투게 된다.
 * 정합성 검사는 낙관적 락이 정상 동작하는 한 원래 거의 항상 "이상 없음"으로 끝나야 하는 저빈도 안전망이라,
 * 별도 스레드 경합을 감수할 만큼 급하지 않다고 판단해 여기 얹었다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirySweeper {

    private final ReservationService reservationService;
    private final TimeSlotInventoryReconciler timeSlotInventoryReconciler;

    @Value("${travelx.reservation.inventory-reconcile-every-n-ticks:5}")
    private int inventoryReconcileEveryNTicks;

    private final AtomicLong tickCount = new AtomicLong(0);

    @Scheduled(fixedDelayString = "${travelx.reservation.expiry-sweep-interval-ms:60000}")
    public void sweep() {
        for (Long id : reservationService.findOverdueReservationIds()) {
            try {
                reservationService.expireOneOverdueReservation(id);
            } catch (ObjectOptimisticLockingFailureException e) {
                reservationService.recordOptimisticLockConflict("sweep_overdue");
                log.info("[ReservationExpirySweeper] 픽업 홀드 만료 처리 중 낙관적 락 충돌 — 다른 요청이 먼저 "
                        + "처리함(취소 등), reservationId={}", id);
            }
        }
        for (Long id : reservationService.findOverduePendingPaymentIds()) {
            try {
                reservationService.expireOnePendingPayment(id);
            } catch (ObjectOptimisticLockingFailureException e) {
                reservationService.recordOptimisticLockConflict("sweep_pending");
                log.info("[ReservationExpirySweeper] 결제 홀드 만료 처리 중 낙관적 락 충돌 — 다른 요청이 먼저 "
                        + "처리함, reservationId={}", id);
            }
        }

        // 기본 60초 * 5틱 = 5분 주기와 동일한 체감 빈도를 유지하되, 별도 스케줄러 스레드는 새로 쓰지 않는다.
        if (tickCount.incrementAndGet() % inventoryReconcileEveryNTicks == 0) {
            timeSlotInventoryReconciler.reconcile();
        }
    }
}
