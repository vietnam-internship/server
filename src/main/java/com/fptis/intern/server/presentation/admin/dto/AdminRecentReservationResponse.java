package com.fptis.intern.server.presentation.admin.dto;

public record AdminRecentReservationResponse(
        String customerName,
        String currencyPair,
        double amount,
        String status
) {
}
