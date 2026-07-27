package com.fptis.intern.server.application.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 두 종류의 만료를 주기적으로 정리한다:
 * 1) 픽업 홀드(2시간) 만료된 RESERVED 예약 → CANCELLED(autoExpired=true, 노쇼), 재고 복원 (PRD §19.1)
 * 2) 결제 홀드(5분) 만료된 PENDING_PAYMENT 예약 → EXPIRED(유령 홀드), 재고 복원 (discussion#16)
 */
@Component
@RequiredArgsConstructor
public class ReservationExpirySweeper {

    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        reservationService.expireOverdueReservations();
        reservationService.expireOverduePendingPayments();
    }
}
