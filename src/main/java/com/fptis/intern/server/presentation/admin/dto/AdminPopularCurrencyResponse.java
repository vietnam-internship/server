package com.fptis.intern.server.presentation.admin.dto;

public record AdminPopularCurrencyResponse(
        String currencyCode,
        long count
) {
}
