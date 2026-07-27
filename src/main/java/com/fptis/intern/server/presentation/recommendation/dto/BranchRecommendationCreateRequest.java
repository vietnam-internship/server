package com.fptis.intern.server.presentation.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record BranchRecommendationCreateRequest(
        @Schema(description = "사용자 현재 위도") double latitude,
        @Schema(description = "사용자 현재 경도") double longitude,
        @Schema(description = "검색 반경 (km)", example = "5.0") @Positive double radiusKm,
        @Schema(description = "환전 희망 통화 코드", example = "USD") @NotBlank String currency,
        @Schema(description = "환전 희망 금액 (외화 기준)", example = "1000.0") @Positive double amount
) {
}
