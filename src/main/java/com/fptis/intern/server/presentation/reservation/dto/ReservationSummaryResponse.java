package com.fptis.intern.server.presentation.reservation.dto;

import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationSummaryResponse(
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
        LocalDateTime paymentExpiresAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {

    public static ReservationSummaryResponse of(Reservation reservation, String branchName) {
        boolean isPendingPayment = reservation.getStatus() == ReservationStatus.PENDING_PAYMENT;
        LocalDateTime paymentExpiresAt = isPendingPayment ? reservation.getPaymentExpiresAt() : null;
        LocalDateTime expiresAt = reservation.getStatus() == ReservationStatus.RESERVED ? reservation.getExpiresAt() : null;
        return new ReservationSummaryResponse(reservation.getId(), reservation.getReservationNumber(),
                reservation.getCurrencyCode(), reservation.getAmount(), reservation.getBranchId(), branchName,
                reservation.getPickupDate(), reservation.getPickupTime().toString(), reservation.getStatus(),
                reservation.getLockedRate(), paymentExpiresAt, expiresAt, reservation.getCreatedAt());
    }
}
