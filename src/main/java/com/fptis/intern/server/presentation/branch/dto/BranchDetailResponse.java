package com.fptis.intern.server.presentation.branch.dto;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * finalRate/isBestRateNearby는 기준 환율(Currency 도메인)이 없어 각각 null/false로 고정된다 — #21에서 연동 예정.
 */
public record BranchDetailResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        Double distanceKm,
        boolean isOpenNow,
        Double finalRate,
        Double preferentialRate,
        boolean reservationAvailable,
        String phone,
        String businessHours,
        String pickupLocationDetail,
        int timeSlotCapacity,
        boolean isBestRateNearby,
        boolean active,
        List<BranchCurrencyRateResponse> currencies
) {

    public static BranchDetailResponse of(Branch branch, List<BranchCurrencyRate> rates) {
        List<BranchCurrencyRateResponse> currencies = rates.stream()
                .map(BranchCurrencyRateResponse::from)
                .toList();
        boolean reservationAvailable = rates.stream().anyMatch(BranchCurrencyRate::hasStock);

        return new BranchDetailResponse(branch.getId(), branch.getName(), branch.getAddress(),
                branch.getLatitude(), branch.getLongitude(), null, branch.isOpenNow(LocalDateTime.now()),
                null, null, reservationAvailable, branch.getPhone(), branch.getBusinessHours(),
                branch.getPickupLocationDetail(), branch.getTimeSlotCapacity(), false, branch.isActive(),
                currencies);
    }
}
