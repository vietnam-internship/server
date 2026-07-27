package com.fptis.intern.server.presentation.branch.dto;

import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import java.time.LocalDateTime;

public record BranchCurrencyRateResponse(
        String currencyCode,
        double preferentialRate,
        Double finalRate,
        double reservationOnlyStock,
        LocalDateTime updatedAt
) {

    public static BranchCurrencyRateResponse from(BranchCurrencyRate rate, Double finalRate) {
        return new BranchCurrencyRateResponse(rate.getCurrencyCode(), rate.getPreferentialRate(),
                finalRate, rate.getReservationOnlyStock(), rate.getUpdatedAt());
    }
}
