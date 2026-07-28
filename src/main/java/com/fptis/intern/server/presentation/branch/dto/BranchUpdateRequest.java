package com.fptis.intern.server.presentation.branch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BranchUpdateRequest(
        @Schema(description = "지점명") String name,
        @Schema(description = "지점 주소") String address,
        @Schema(description = "위도") Double latitude,
        @Schema(description = "경도") Double longitude,
        @Schema(description = "지점 연락처") String phone,
        @Schema(description = "영업시간 (직접 지정 시 openTime/closeTime보다 우선)") String businessHours,
        @Schema(description = "오픈 시각 (예: 09:00)") String openTime,
        @Schema(description = "마감 시각 (예: 18:00)") String closeTime,
        @Schema(description = "픽업 장소 상세 안내") String pickupLocationDetail,
        @Schema(description = "픽업 시간 슬롯당 예약 정원") Integer timeSlotCapacity,
        @Schema(description = "취급 통화 코드 목록") List<String> supportedCurrencies,
        @Schema(description = "지점 활성 여부") Boolean active
) {
}
