package com.fptis.intern.server.presentation.ai.dto;

import com.fptis.intern.server.domain.ai.BacktestResult.StrategyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record BacktestCreateRequest(
        @NotNull(message = "전략 타입은 필수입니다.")
        StrategyType strategyType,

        @NotNull(message = "테스트 시작일은 필수입니다.")
        LocalDate periodStart,

        @NotNull(message = "테스트 종료일은 필수입니다.")
        LocalDate periodEnd,

        @NotNull(message = "전체 시그널 수는 필수입니다.")
        @PositiveOrZero
        Integer totalSignals,

        @NotNull(message = "정답 시그널 수는 필수입니다.")
        @PositiveOrZero
        Integer correctSignals,

        @NotNull(message = "정확도는 필수입니다.")
        Double accuracyRate
) {
}
