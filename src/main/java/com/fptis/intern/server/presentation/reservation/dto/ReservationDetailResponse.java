package com.fptis.intern.server.presentation.reservation.dto;

import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import com.fptis.intern.server.presentation.branch.dto.BranchSummaryResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * lockedRate/amountFrom은 기준 환율(Currency 도메인)이 없어 항상 null이다 — #26에서 연동 예정.
 */
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

        return new ReservationDetailResponse(reservation.getId(), reservation.getReservationNumber(),
                reservation.getCurrencyCode(), reservation.getAmount(), reservation.getBranchId(), branch.name(),
                reservation.getPickupDate(), reservation.getPickupTime().toString(), reservation.getStatus(),
                reservation.getLockedRate(), expiresAt, reservation.getCreatedAt(),
                null, reservation.getAmount(), qrPayload, reservation.getPickedUpAt(), branch);
    }
}
