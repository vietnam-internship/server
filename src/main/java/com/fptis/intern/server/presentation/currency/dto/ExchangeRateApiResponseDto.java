package com.fptis.intern.server.presentation.currency.dto;

import java.util.Map;

public record ExchangeRateApiResponseDto(
        String result,
        String base_code,
        Map<String, Double> conversion_rates
) {
}
