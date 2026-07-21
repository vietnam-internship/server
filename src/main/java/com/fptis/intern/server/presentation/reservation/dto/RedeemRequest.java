package com.fptis.intern.server.presentation.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RedeemRequest(
        @NotBlank String qrToken,
        @NotNull Boolean idVerified
) {
}
