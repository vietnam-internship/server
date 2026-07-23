package com.fptis.intern.server.presentation.branch.dto;

import com.fptis.intern.server.domain.branch.Branch;

/**
 * finalRate는 기준 환율(Currency 도메인)이 없어 항상 null이다 — #21에서 연동 예정.
 */
public record BranchSummaryResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        Double distanceKm,
        boolean isOpenNow,
        Double finalRate,
        Double preferentialRate,
        boolean reservationAvailable
) {

    public static BranchSummaryResponse of(Branch branch, Double distanceKm, boolean isOpenNow,
                                            Double preferentialRate, boolean reservationAvailable) {
        return new BranchSummaryResponse(branch.getId(), branch.getName(), branch.getAddress(),
                branch.getLatitude(), branch.getLongitude(), distanceKm, isOpenNow,
                null, preferentialRate, reservationAvailable);
    }
}
