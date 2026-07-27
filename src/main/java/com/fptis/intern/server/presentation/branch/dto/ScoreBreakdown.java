package com.fptis.intern.server.presentation.branch.dto;

public record ScoreBreakdown(
        double distanceScore,
        double rateScore,
        double availabilityScore,
        double reservationScore
) {
}
