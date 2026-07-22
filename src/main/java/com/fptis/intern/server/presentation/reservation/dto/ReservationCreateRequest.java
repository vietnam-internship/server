package com.fptis.intern.server.presentation.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record ReservationCreateRequest(
        @NotBlank String currencyCode,
        @NotNull Long branchId,
        @Positive double amount,
        @NotNull LocalDate pickupDate,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):(00|30)$", message = "30분 슬롯 시작 시각(HH:00 또는 HH:30)만 가능합니다.")
        String pickupTime
) {
}
