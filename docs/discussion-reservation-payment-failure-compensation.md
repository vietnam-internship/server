### 배경

`docs/discussion-reservation-transaction-boundary.md`에서 신청 흐름의 트랜잭션을 Phase 0~3으로 쪼갰습니다. Phase 1(`ReservationHoldService.createHold`, 재고/슬롯 락 + 유저 불변식 확인)과 Phase 2(`PaymentService.createPaymentIntent`, Stripe 호출)가 서로 다른 트랜잭션으로 분리되면서, 원래 discussion#16이 의도했던 "락이 PG 응답 속도에 종속되지 않는다"는 목표는 달성했지만, 동시에 기존에 공짜로 따라오던 안전망 하나가 사라졌습니다.

- **이전(단일 트랜잭션)**: Stripe 호출이 실패하면 트랜잭션 전체가 롤백 — 예약도, 재고/슬롯 차감도 전부 없었던 일이 됨. 사용자는 즉시 재시도 가능.
- **이후(분리)**: Phase 1이 이미 커밋된 뒤 Phase 2가 실패하면, PENDING_PAYMENT 예약과 차감된 재고/슬롯은 그대로 남고 Payment row만 없는 상태가 됩니다. 게다가 `discussion-reservation-transaction-boundary.md`에서 함께 강화한 "동시 PENDING_PAYMENT 1건 제한"에 걸려, 이 사용자는 본인 잘못이 아닌 PG 장애로 5분 TTL 동안 재예약이 막힙니다.

이걸 그냥 두면(5분 TTL 스윕러가 정리) 충분한지, 즉시 보상(취소 + 재고/슬롯 복원)을 태워야 하는지가 이번 논의 주제입니다.

---

### 왜 "무조건 보상"이 아닌가

Stripe 호출 실패의 원인은 두 가지로 나뉩니다.

1. **확정 실패** — Stripe가 요청을 받고 명확히 거절(카드 오류, 요청 파라미터 오류, 인증/권한 오류 등). 이 경우 Stripe 쪽에 PaymentIntent가 생성되지 않았다는 걸 확신할 수 있습니다.
2. **결과 불확실** — 네트워크 타임아웃(`ApiConnectionException`), Stripe 서버 오류(`ApiException`), 동일 idempotency key 재사용 충돌(`IdempotencyException`). 이땐 Stripe가 실제로 PaymentIntent를 만들었는데 응답만 유실됐을 가능성이 있습니다.

2번 상황에서 즉시 보상(예약 취소)을 하면, Stripe엔 살아있는 PaymentIntent가 있는데 우리 예약은 취소된 상태로 어긋날 수 있습니다 — `reservation-payment-review.md` §4-①/④가 이미 지적한 "결제는 됐는데 예약/환불이 없는" 문제와 같은 종류의 불일치를 스스로 만드는 셈입니다. 그래서 **확정 실패만 즉시 보상**하고, 결과 불확실은 기존 TTL 스윕에 맡기는 쪽으로 결정했습니다.

---

### 구현

**1. `PaymentGatewayImpl`이 Stripe 예외를 두 그룹으로 분류**

```
CardException, InvalidRequestException(RateLimitException 포함), AuthenticationException(PermissionException 포함)
  → BusinessErrorCode.PAYMENT_INTENT_CREATE_FAILED (확정 실패)

ApiConnectionException, ApiException, IdempotencyException, 그 외 미분류 StripeException
  → BusinessErrorCode.PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN (결과 불확실, 보수적으로 이쪽에 배정)
```

미분류 타입을 "확정 실패" 대신 "결과 불확실" 쪽에 넣은 이유: 잘못 보상하는 것(살아있는 intent를 취소)이 안 보상하는 것(5분 기다리는 것)보다 비쌉니다.

**2. `ReservationService.createReservation`이 확정 실패에만 즉시 보상**

```java
try {
    intent = paymentService.createPaymentIntent(reservation);
} catch (BusinessException e) {
    if (e.getErrorCode() == BusinessErrorCode.PAYMENT_INTENT_CREATE_FAILED) {
        self.expireOnePendingPayment(reservation.getId());
    }
    throw e;
}
```

새 메서드를 만들지 않고 기존 `expireOnePendingPayment`(스윕러가 5분 TTL 만료 시 호출하는 것과 정확히 같은 메서드: `expireHold()` + 재고/슬롯 복원, `@Transactional(REQUIRES_NEW)`, 상태가 이미 `PENDING_PAYMENT`가 아니면 멱등 no-op)를 재사용했습니다. 즉 "그 정리를 5분 기다리지 않고 지금 바로 한다"는 것과 같은 동작입니다.

---

### 남는 리스크 / 후속 과제

- **재시도는 아직 안 함**: `paymentGateway.createIntent`는 이미 idempotency key(`payment.getIdempotencyKey()`)를 쓰고 있어 같은 키로 재시도해도 중복 생성되지 않습니다. 하지만 지금은 실패 시 바로 예외를 던질 뿐, 재시도는 하지 않습니다. "결과 불확실" 케이스를 몇 번 재시도해서 확정 실패/성공 중 하나로 좁히는 로직은 이번 범위에 넣지 않았습니다.
- **역조회 워커 부재**: `reservation-payment-review.md` §6이 지적한 대로, "결과 불확실" 상태로 남는 케이스(Stripe는 만들었는데 우리는 모르는 PaymentIntent)를 능동적으로 찾아내는 배치가 없습니다. 지금은 5분 TTL 스윕이 유일한 안전망이라, 그 사이에 뒤늦게 웹훅이 오면 `PaymentService.handlePaymentSucceeded`의 "홀드가 이미 종료된 뒤 결제 승인" 분기(수동 정산 로그만 남김)로 흘러갑니다.
- **에러 메시지**: `PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN`의 사용자 메시지("잠시 후 예약 상태를 다시 확인해주세요")가 실제로 프론트에서 어떻게 노출되는지는 이번 범위 밖입니다.
