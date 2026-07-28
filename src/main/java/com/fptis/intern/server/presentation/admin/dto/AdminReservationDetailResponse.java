package com.fptis.intern.server.presentation.admin.dto;

public record AdminReservationDetailResponse(
        Long id,
        String reservationNumber,
        String customerName,
        String currencyPair,
        double amount,
        String status,
        String branchName,
        String pickupDetail
) {
}
