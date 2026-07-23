package com.fptis.intern.server.presentation.currency.dto;

import java.util.List;

public record CurrencyListResponse(
        List<CurrencySummary> results,
        List<CurrencySummary> recentSearches,
        List<CurrencySummary> popularCurrencies
) {
    public static CurrencyListResponse of(List<CurrencySummary> results, List<CurrencySummary> recentSearches, List<CurrencySummary> popularCurrencies) {
        return new CurrencyListResponse(results, recentSearches, popularCurrencies);
    }
}
