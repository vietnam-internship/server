package com.fptis.intern.server.presentation.admin.dto;

public record AdminReservationSummaryResponse(
        Long id,
        String reservationNumber,
        String customerName,
        String currencyPair,
        double amount,
        String status
) {
}
