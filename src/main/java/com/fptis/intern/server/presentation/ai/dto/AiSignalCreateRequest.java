package com.fptis.intern.server.presentation.ai.dto;

import com.fptis.intern.server.domain.ai.RecommendationSignal.SignalType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AiSignalCreateRequest(
        @NotNull(message = "시그널 타입은 필수입니다.")
        SignalType signalType,

        @NotNull(message = "분석 기간(일)은 필수입니다.")
        @Positive(message = "분석 기간은 양수여야 합니다.")
        Integer windowDays,

        @NotNull(message = "시그널 값은 필수입니다.")
        Double value
) {
}
