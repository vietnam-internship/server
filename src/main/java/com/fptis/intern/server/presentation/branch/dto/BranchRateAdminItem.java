package com.fptis.intern.server.presentation.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BranchRateAdminItem(
        @NotBlank String currencyCode,
        @NotNull Double feePercent
) {
}
