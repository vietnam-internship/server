package com.fptis.intern.server.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * discussion#16(Hold→Pay 분리)의 두 TTL을 env로 분리한다 — Reservation 엔티티는 Spring 컨텍스트에
 * 의존하면 안 되므로, 값은 여기서 읽어 서비스 레이어(ReservationHoldService/PaymentService)가
 * 엔티티 메서드에 파라미터로 넘긴다.
 */
@ConfigurationProperties(prefix = "travelx.reservation")
public record ReservationTimingProperties(int paymentHoldMinutes, int pickupHoldHours,
                                           int inventoryReconcileEveryNTicks) {
}
