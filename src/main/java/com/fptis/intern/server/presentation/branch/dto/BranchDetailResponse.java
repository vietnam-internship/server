package com.fptis.intern.server.presentation.branch.dto;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 최상위 finalRate는 BranchSummary 상속 필드로, currency 쿼리 파라미터가 있는 목록 조회에서만 채워진다
 * (상세 조회 엔드포인트에는 currency 파라미터가 없어 항상 null) — 통화별 최종 환율은 currencies[].finalRate를 본다.
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

    public static BranchDetailResponse of(Branch branch, List<BranchCurrencyRate> rates,
                                           Map<String, Double> finalRateByCurrency, boolean isBestRateNearby) {
        List<BranchCurrencyRateResponse> currencies = rates.stream()
                .map(rate -> BranchCurrencyRateResponse.from(rate, finalRateByCurrency.get(rate.getCurrencyCode())))
                .toList();
        boolean reservationAvailable = rates.stream().anyMatch(BranchCurrencyRate::hasStock);

        return new BranchDetailResponse(branch.getId(), branch.getName(), branch.getAddress(),
                branch.getLatitude(), branch.getLongitude(), null, branch.isOpenNow(LocalDateTime.now()),
                null, null, reservationAvailable, branch.getPhone(), branch.getBusinessHours(),
                branch.getPickupLocationDetail(), branch.getTimeSlotCapacity(), isBestRateNearby, branch.isActive(),
                currencies);
    }
}
