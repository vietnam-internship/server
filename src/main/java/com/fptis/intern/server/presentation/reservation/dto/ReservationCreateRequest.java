package com.fptis.intern.server.presentation.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record ReservationCreateRequest(
        @Schema(description = "환전 통화 코드 (예: USD)") @NotBlank String currencyCode,
        @Schema(description = "픽업 지점 ID") @NotNull Long branchId,
        @Schema(description = "환전 금액") @Positive double amount,
        @Schema(description = "픽업 예정일 (yyyy-MM-dd)") @NotNull LocalDate pickupDate,
        @Schema(description = "픽업 예정 시각, 30분 슬롯 (HH:00 또는 HH:30)", example = "14:30")
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):(00|30)$", message = "30분 슬롯 시작 시각(HH:00 또는 HH:30)만 가능합니다.")
        String pickupTime
) {
}
