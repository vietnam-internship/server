package com.fptis.intern.server.presentation.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MacroIndicatorCreateRequest(
        @NotBlank(message = "국가 코드는 필수입니다.")
        String countryCode,

        @NotBlank(message = "지표 종류는 필수입니다.")
        String indicatorType,

        @NotNull(message = "지표 값은 필수입니다.")
        Double value,

        @NotNull(message = "기록 일자는 필수입니다.")
        LocalDate recordedAt
) {
}
