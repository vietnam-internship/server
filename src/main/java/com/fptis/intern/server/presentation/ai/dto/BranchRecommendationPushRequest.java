package com.fptis.intern.server.presentation.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BranchRecommendationPushRequest(
        @Schema(description = "결과를 수신할 추천 세션 ID") @NotNull Long sessionId,
        @Schema(description = "AI가 산출한 환전소 랭킹 목록 (rank 오름차순)") @NotNull @NotEmpty @Valid List<RankedBranchItem> rankedBranches
) {
}
