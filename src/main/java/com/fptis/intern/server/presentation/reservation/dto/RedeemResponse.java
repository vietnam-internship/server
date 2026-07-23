package com.fptis.intern.server.presentation.reservation.dto;

import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import java.time.LocalDateTime;

public record RedeemResponse(
        Long reservationId,
        ReservationStatus status,
        LocalDateTime pickedUpAt,
        ReservationDetailResponse summary
) {

    public static RedeemResponse of(Reservation reservation, ReservationDetailResponse summary) {
        return new RedeemResponse(reservation.getId(), reservation.getStatus(), reservation.getPickedUpAt(), summary);
    }
}
