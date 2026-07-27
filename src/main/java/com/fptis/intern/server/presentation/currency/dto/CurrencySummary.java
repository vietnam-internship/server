package com.fptis.intern.server.presentation.currency.dto;

import com.fptis.intern.server.domain.currency.Currency;
import java.time.LocalDateTime;

public record CurrencySummary(
        Long id,
        String code,
        String country,
        double buyRate,
        double sellRate,
        LocalDateTime updatedAt
) {
    public static CurrencySummary from(Currency currency) {
        return new CurrencySummary(
                currency.getId(),
                currency.getCode(),
                currency.getCountry(),
                currency.getBuyRate(),
                currency.getSellRate(),
                currency.getUpdatedAt()
        );
    }
}
