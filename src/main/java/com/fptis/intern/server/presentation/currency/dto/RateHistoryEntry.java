package com.fptis.intern.server.presentation.currency.dto;

import com.fptis.intern.server.domain.currency.ExchangeRateHistory;
import java.time.LocalDate;

public record RateHistoryEntry(
        LocalDate date,
        double rate
) {
    public static RateHistoryEntry from(ExchangeRateHistory history) {
        return new RateHistoryEntry(
                history.getRecordedAt(),
                history.getRate()
        );
    }
}
