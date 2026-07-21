package com.fptis.intern.server.presentation.branch.dto;

import java.util.List;

/**
 * 부분 수정 — null이 아닌 필드만 갱신한다.
 */
public record BranchUpdateRequest(
        String name,
        String address,
        Double latitude,
        Double longitude,
        String phone,
        String businessHours,
        String pickupLocationDetail,
        Integer timeSlotCapacity,
        List<String> supportedCurrencies,
        Boolean active
) {
}
