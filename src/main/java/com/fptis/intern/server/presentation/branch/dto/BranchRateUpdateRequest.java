package com.fptis.intern.server.presentation.branch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record BranchRateUpdateRequest(
        @Schema(description = "통화 코드 (예: USD)") @NotBlank String currencyCode,
        @Schema(description = "우대율") Double preferentialRate,
        @Schema(description = "예약 전용 재고") Double reservationOnlyStock
) {
}
