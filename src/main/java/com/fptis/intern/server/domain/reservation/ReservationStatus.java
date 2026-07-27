package com.fptis.intern.server.domain.reservation;

/**
 * discussion#16(방안 2: Hold→Pay 분리)에 따른 상태 전이:
 * PENDING_PAYMENT -(결제 승인)-> RESERVED -(픽업)-> COMPLETED
 * PENDING_PAYMENT -(결제 TTL 만료, 5분)-> EXPIRED
 * RESERVED -(사용자 취소 | 픽업 TTL 만료, 2시간)-> CANCELLED
 * 결제 실패(카드 거절 등)는 상태를 바꾸지 않는다 — Payment 쪽 상태로만 기록하고
 * PENDING_PAYMENT를 유지해 TTL 안에서 재시도할 수 있게 한다.
 * UI 라벨: PENDING_PAYMENT=Pending, RESERVED=Upcoming, COMPLETED=Completed,
 * CANCELLED=Cancelled, EXPIRED=Expired.
 */
public enum ReservationStatus {
    PENDING_PAYMENT,
    RESERVED,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
