package com.fptis.intern.server.presentation.branch.dto;

import com.fptis.intern.server.domain.branch.Branch;

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
                                            Double preferentialRate, Double finalRate, boolean reservationAvailable) {
        return new BranchSummaryResponse(branch.getId(), branch.getName(), branch.getAddress(),
                branch.getLatitude(), branch.getLongitude(), distanceKm, isOpenNow,
                finalRate, preferentialRate, reservationAvailable);
    }
}
