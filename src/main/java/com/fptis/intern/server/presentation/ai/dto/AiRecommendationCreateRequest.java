package com.fptis.intern.server.presentation.ai.dto;

import com.fptis.intern.server.domain.ai.AiRecommendation.RecommendationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AiRecommendationCreateRequest(
        @NotNull(message = "추천 등급은 필수입니다.")
        RecommendationType recommendation,

        String rationale,

        @NotNull(message = "신뢰도 점수는 필수입니다.")
        Double confidenceScore,

        @NotBlank(message = "모델 버전은 필수입니다.")
        String modelVersion,

        @NotNull(message = "매핑할 시그널 ID 목록은 필수입니다.")
        List<Long> signalIds
) {
}
