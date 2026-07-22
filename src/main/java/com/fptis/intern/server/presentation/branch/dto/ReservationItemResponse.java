package com.fptis.intern.server.presentation.branch.dto;

import java.math.BigDecimal;

public record ReservationItemResponse(
        Long id,
        Long userId,
        Long currencyId,
        BigDecimal amount,
        String status,
        BigDecimal lockedRate
) {
    public static ReservationItemResponse of(Long id, Long userId, Long currencyId, BigDecimal amount, String status, BigDecimal lockedRate) {
        return new ReservationItemResponse(id, userId, currencyId, amount, status, lockedRate);
    }
}
