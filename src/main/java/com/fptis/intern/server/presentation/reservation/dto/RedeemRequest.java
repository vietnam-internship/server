package com.fptis.intern.server.presentation.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RedeemRequest(
        @Schema(description = "예약 확인용 QR 토큰") @NotBlank String qrToken,
        @Schema(description = "창구 직원의 신원 확인 여부") @NotNull Boolean idVerified
) {
}
