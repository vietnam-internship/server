package com.fptis.intern.server.presentation.ai.dto;

import com.fptis.intern.server.presentation.branch.dto.ScoreBreakdown;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RankedBranchItem(
        @Schema(description = "추천 대상 환전소(지점) ID") @NotNull Long branchId,
        @Schema(description = "순위 (1위부터 시작)") @Min(1) int ranking,
        @Schema(description = "AI 산출 종합 점수 (0.0 ~ 1.0)", example = "0.87") double score,
        @Schema(description = "요소별 세부 점수 (null이면 breakdown 없이 저장)") ScoreBreakdown breakdown
) {
}
