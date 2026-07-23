package com.fptis.intern.server.presentation.branch.dto;

import java.util.List;

public record BranchReservationListResponse(
        List<ReservationItemResponse> reservations
) {
    public static BranchReservationListResponse from(List<ReservationItemResponse> reservations) {
        return new BranchReservationListResponse(reservations);
    }
}
