package com.fptis.intern.server.application.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 재고 홀드(2시간) 만료된 RESERVED 예약을 주기적으로 CANCELLED(autoExpired=true, 노쇼)로
 * 전환하고 재고를 복원한다. (PRD §19.1)
 */
@Component
@RequiredArgsConstructor
public class ReservationExpirySweeper {

    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        reservationService.expireOverdueReservations();
    }
}
