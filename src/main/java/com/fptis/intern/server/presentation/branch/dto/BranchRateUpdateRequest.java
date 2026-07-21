package com.fptis.intern.server.presentation.branch.dto;

import jakarta.validation.constraints.NotBlank;

public record BranchRateUpdateRequest(
        @NotBlank String currencyCode,
        Double preferentialRate,
        Double reservationOnlyStock
) {
}
