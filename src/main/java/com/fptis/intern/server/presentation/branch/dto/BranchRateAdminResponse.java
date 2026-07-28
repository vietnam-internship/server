package com.fptis.intern.server.presentation.branch.dto;

public record BranchRateAdminResponse(
        String currencyCode,
        Double buyRate,
        Double sellRate,
        double feePercent
) {
}
