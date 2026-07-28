package com.fptis.intern.server.presentation.admin.dto;

import java.util.List;

public record AdminDashboardResponse(
        long totalUsers,
        long pendingReservationsCount,
        List<AdminPopularCurrencyResponse> popularCurrencies,
        List<AdminRecentReservationResponse> recentReservations
) {
}
