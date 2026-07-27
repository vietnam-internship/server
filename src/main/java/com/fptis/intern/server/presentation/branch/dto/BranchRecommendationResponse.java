package com.fptis.intern.server.presentation.branch.dto;

import java.util.List;

public record BranchRecommendationResponse(
        List<BranchRecommendation> results,
        String disclaimer
) {

    private static final String DISCLAIMER = "본 추천은 투자·금융 조언이 아니며 참고용으로만 제공됩니다.";

    public static BranchRecommendationResponse of(List<BranchRecommendation> results) {
        return new BranchRecommendationResponse(results, DISCLAIMER);
    }
}
