package com.fptis.intern.server.presentation.branch.dto;

import com.fptis.intern.server.domain.branch.Branch;
import java.time.LocalDateTime;

public record BranchRecommendation(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        int ranking,
        Double distanceKm,
        boolean isOpenNow,
        Double finalRate,
        Double preferentialRate,
        boolean reservationAvailable,
        double totalScore,
        ScoreBreakdown breakdown,
        boolean isBestRateNearby
) {

    public static BranchRecommendation of(Branch branch, int ranking, double distanceKm, Double preferentialRate,
                                           Double finalRate, boolean reservationAvailable, double totalScore,
                                           ScoreBreakdown breakdown, boolean isBestRateNearby) {
        return new BranchRecommendation(
                branch.getId(), branch.getName(), branch.getAddress(),
                branch.getLatitude(), branch.getLongitude(),
                ranking, distanceKm, branch.isOpenNow(LocalDateTime.now()),
                finalRate, preferentialRate, reservationAvailable,
                totalScore, breakdown, isBestRateNearby);
    }
}
