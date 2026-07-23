package com.fptis.intern.server.presentation.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BranchCreateRequest(
        @NotBlank String name,
        @NotBlank String address,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotBlank String phone,
        @NotBlank String businessHours,
        String pickupLocationDetail,
        @NotNull Integer timeSlotCapacity,
        List<String> supportedCurrencies
) {
}
