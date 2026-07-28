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
        LocalDateTime paymentExpiresAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        Double amountFrom,
        double amountTo,
        String qrPayload,
        LocalDateTime pickedUpAt,
        BranchSummaryResponse branch,
        String paymentClientSecret
) {

    public static ReservationDetailResponse of(Reservation reservation, BranchSummaryResponse branch) {
        return of(reservation, branch, null);
    }

    /**
     * paymentClientSecret은 예약 생성 응답에서만 채운다 — Stripe.js가 결제창을 띄우는 데 필요한
     * 값이며, 우리 DB에는 저장하지 않으므로(보안 권고) 이후 재조회 응답에서는 항상 null이다.
     */
    public static ReservationDetailResponse of(Reservation reservation, BranchSummaryResponse branch,
                                                String paymentClientSecret) {
        boolean isReserved = reservation.getStatus() == ReservationStatus.RESERVED;
        boolean isPendingPayment = reservation.getStatus() == ReservationStatus.PENDING_PAYMENT;
        LocalDateTime paymentExpiresAt = isPendingPayment ? reservation.getPaymentExpiresAt() : null;
        LocalDateTime expiresAt = isReserved ? reservation.getExpiresAt() : null;
        // "{branchId}:{reservationId}:{token}" — 관리자 화면이 QR 하나만 보고 리딤 API
        // (POST /branches/{id}/reservations/{reservationId}/redeem)를 바로 호출할 수 있게 한다.
        String qrPayload = isReserved
                ? reservation.getBranchId() + ":" + reservation.getId() + ":" + reservation.getQrToken()
                : null;
        Double amountFrom = reservation.getLockedRate() != null
                ? reservation.getAmount() * reservation.getLockedRate()
                : null;

        return new ReservationDetailResponse(reservation.getId(), reservation.getReservationNumber(),
                reservation.getCurrencyCode(), reservation.getAmount(), reservation.getBranchId(), branch.name(),
                reservation.getPickupDate(), reservation.getPickupTime().toString(), reservation.getStatus(),
                reservation.getLockedRate(), paymentExpiresAt, expiresAt, reservation.getCreatedAt(),
                amountFrom, reservation.getAmount(), qrPayload, reservation.getPickedUpAt(), branch, paymentClientSecret);
    }
}
