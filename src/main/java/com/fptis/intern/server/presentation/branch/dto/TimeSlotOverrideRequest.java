package com.fptis.intern.server.presentation.branch.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record TimeSlotOverrideRequest(
        @NotNull(message = "대상 일자는 필수입니다.")
        LocalDate targetDate,

        @NotNull(message = "시작 시간은 필수입니다.")
        LocalTime startTime,

        @NotNull(message = "종료 시간은 필수입니다.")
        LocalTime endTime,

        @NotNull(message = "수용 인원 제한 값은 필수입니다.")
        Integer capacityLimit,

        @NotNull(message = "차단(블락) 여부는 필수입니다.")
        Boolean isBlocked
) {
}
