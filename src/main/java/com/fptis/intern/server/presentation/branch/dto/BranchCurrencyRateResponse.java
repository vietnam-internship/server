package com.fptis.intern.server.presentation.branch.dto;

import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import java.time.LocalDateTime;

/**
 * finalRate는 기준 환율(Currency 도메인)이 없어 항상 null이다 — #21에서 연동 예정.
 */
public record BranchCurrencyRateResponse(
        String currencyCode,
        double preferentialRate,
        Double finalRate,
        double reservationOnlyStock,
        LocalDateTime updatedAt
) {

    public static BranchCurrencyRateResponse from(BranchCurrencyRate rate) {
        return new BranchCurrencyRateResponse(rate.getCurrencyCode(), rate.getPreferentialRate(),
                null, rate.getReservationOnlyStock(), rate.getUpdatedAt());
    }
}
