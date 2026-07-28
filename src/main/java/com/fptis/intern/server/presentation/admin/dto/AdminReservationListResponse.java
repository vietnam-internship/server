package com.fptis.intern.server.presentation.admin.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record AdminReservationListResponse(
        List<AdminReservationSummaryResponse> reservations,
        int page,
        int totalPages,
        long totalElements
) {

    public static AdminReservationListResponse of(Page<AdminReservationSummaryResponse> page) {
        return new AdminReservationListResponse(page.getContent(), page.getNumber(), page.getTotalPages(),
                page.getTotalElements());
    }
}
