package com.fptis.intern.server.presentation.currency.dto;

import com.fptis.intern.server.domain.currency.Currency;
import java.time.LocalDateTime;

public record CurrencyDetail(
        Long id,
        String code,
        String country,
        double buyRate,
        double sellRate,
        LocalDateTime updatedAt,
        boolean highVolatility
) {
    public static CurrencyDetail of(Currency currency, boolean highVolatility) {
        return new CurrencyDetail(
                currency.getId(),
                currency.getCode(),
                currency.getCountry(),
                currency.getBuyRate(),
                currency.getSellRate(),
                currency.getUpdatedAt(),
                highVolatility
        );
    }
}
