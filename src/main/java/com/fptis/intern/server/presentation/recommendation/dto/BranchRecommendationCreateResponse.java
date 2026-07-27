package com.fptis.intern.server.presentation.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record BranchRecommendationCreateResponse(
        @Schema(description = "추천 세션 ID — GET /branches/recommendations?sessionId={id}로 결과 폴링") Long sessionId
) {
}
