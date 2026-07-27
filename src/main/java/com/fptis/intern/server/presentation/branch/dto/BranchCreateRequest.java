package com.fptis.intern.server.presentation.branch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BranchCreateRequest(
        @Schema(description = "지점명") @NotBlank String name,
        @Schema(description = "지점 주소") @NotBlank String address,
        @Schema(description = "위도") @NotNull Double latitude,
        @Schema(description = "경도") @NotNull Double longitude,
        @Schema(description = "지점 연락처") @NotBlank String phone,
        @Schema(description = "영업시간") @NotBlank String businessHours,
        @Schema(description = "픽업 장소 상세 안내") String pickupLocationDetail,
        @Schema(description = "픽업 시간 슬롯당 예약 정원") @NotNull Integer timeSlotCapacity,
        @Schema(description = "취급 통화 코드 목록") List<String> supportedCurrencies
) {
}
