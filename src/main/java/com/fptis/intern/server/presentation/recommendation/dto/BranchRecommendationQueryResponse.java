package com.fptis.intern.server.presentation.recommendation.dto;

import com.fptis.intern.server.presentation.branch.dto.BranchRecommendation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BranchRecommendationQueryResponse(
        @Schema(description = "세션 상태 (PENDING / COMPLETED / FAILED)", example = "COMPLETED") String status,
        @Schema(description = "추천 환전소 목록 (ranking 오름차순). PENDING/FAILED이면 빈 배열") List<BranchRecommendation> results,
        @Schema(description = "면책 고지 문구") String disclaimer
) {

    private static final String DISCLAIMER = "본 추천은 투자·금융 조언이 아니며 참고용으로만 제공됩니다.";

    public static BranchRecommendationQueryResponse of(String status, List<BranchRecommendation> results) {
        return new BranchRecommendationQueryResponse(status, results, DISCLAIMER);
    }
}
