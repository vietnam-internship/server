package com.fptis.intern.server.presentation.admin;

import com.fptis.intern.server.domain.reservation.ReservationStatus;

/** Single source of truth for the mapping in spec §4 — used by every admin read model. */
public final class AdminReservationStatusMapper {

    private AdminReservationStatusMapper() {
    }

    public static String toAdminBucket(ReservationStatus status) {
        return switch (status) {
            case RESERVED, PENDING_PAYMENT -> "pending";
            case COMPLETED -> "completed";
            case CANCELLED, EXPIRED -> "cancelled";
        };
    }
}
