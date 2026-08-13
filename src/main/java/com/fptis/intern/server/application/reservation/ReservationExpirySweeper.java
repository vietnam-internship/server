package com.fptis.intern.server.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirySweeper {

    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${travelx.reservation.expiry-sweep-interval-ms:60000}")
    public void sweep() {
        for (Long id : reservationService.findOverdueReservationIds()) {
            try {
                reservationService.expireOneOverdueReservation(id);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.info("[ReservationExpirySweeper] 픽업 홀드 만료 처리 중 낙관적 락 충돌 — 다른 요청이 먼저 "
                        + "처리함(취소 등), reservationId={}", id);
                reservationService.recordOptimisticLockConflict("sweep_overdue");
            }
        }
        for (Long id : reservationService.findOverduePendingPaymentIds()) {
            try {
                reservationService.expireOnePendingPayment(id);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.info("[ReservationExpirySweeper] 결제 홀드 만료 처리 중 낙관적 락 충돌 — 다른 요청이 먼저 "
                        + "처리함, reservationId={}", id);
                reservationService.recordOptimisticLockConflict("sweep_pending");
            }
        }
    }
}
