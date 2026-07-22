package com.fptis.intern.server.presentation.reservation.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ReservationPageResponse(
        List<ReservationSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static ReservationPageResponse of(Page<ReservationSummaryResponse> page) {
        return new ReservationPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
