package com.fptis.intern.server.presentation.reservation.dto;

import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import com.fptis.intern.server.presentation.branch.dto.BranchSummaryResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationDetailResponse(
        Long id,
        String reservationNumber,
        String currencyCode,
        double amount,
        Long branchId,
        String branchName,
        LocalDate pickupDate,
        String pickupTime,
        ReservationStatus status,
        Double lockedRate,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        Double amountFrom,
        double amountTo,
        String qrPayload,
        LocalDateTime pickedUpAt,
        BranchSummaryResponse branch
) {

    public static ReservationDetailResponse of(Reservation reservation, BranchSummaryResponse branch) {
        boolean isReserved = reservation.getStatus() == ReservationStatus.RESERVED;
        LocalDateTime expiresAt = isReserved ? reservation.getExpiresAt() : null;
        String qrPayload = isReserved ? reservation.getQrToken() : null;
        Double amountFrom = reservation.getLockedRate() != null
                ? reservation.getAmount() * reservation.getLockedRate()
                : null;

        return new ReservationDetailResponse(reservation.getId(), reservation.getReservationNumber(),
                reservation.getCurrencyCode(), reservation.getAmount(), reservation.getBranchId(), branch.name(),
                reservation.getPickupDate(), reservation.getPickupTime().toString(), reservation.getStatus(),
                reservation.getLockedRate(), expiresAt, reservation.getCreatedAt(),
                amountFrom, reservation.getAmount(), qrPayload, reservation.getPickedUpAt(), branch);
    }
}
