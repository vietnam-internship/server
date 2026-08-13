# 신청 → 결제 호출 흐름 (컨트롤러 ~ Stripe)

작성 목적: "신청부터 결제까지 컨트롤러 어디서 어떤 흐름으로 호출되는지"를 코드 그대로 추적. 트랜잭션 경계가 실제로 어디서 열리고 닫히는지가 핵심이라, 각 호출에 `@Transactional` 전파 방식을 표시했다.

---

## 1. 예약 생성(신청) — `POST /reservations`

```
[Client]
  └─ POST /reservations
       │
       ▼
ReservationController.createReservation(userId, request)          presentation/reservation/ReservationController.java:41
  └─ reservationService.createReservation(userId, request)
       │
       ▼
ReservationService.createReservation(userId, request)              application/reservation/ReservationService.java:78
  @Transactional                                                   ← 트랜잭션 A 시작 (클래스 기본값 readOnly=true를 여기서 명시적으로 덮어씀, line 71-77 주석)
  │
  ├─ 1. userRepository.findById(userId)                            line 80
  ├─ 2. assertNoShowLimitNotExceeded(userId, now)                   line 84
  ├─ 3. assertNoConcurrentPendingPayment(userId)                    line 85
  ├─ 4. branchRepository.findById / currencyRepository.findByCode
  │     / branchCurrencyRateRepository.findRate                     line 88-95  (금액 한도 검증, #26)
  │
  ├─ 5. reservationHoldService.createHold(userId, request, now)     line 97
  │        │
  │        ▼
  │     ReservationHoldService.createHold(...)                      application/reservation/ReservationHoldService.java:42
  │       @Transactional (기본 REQUIRED)                            ← 트랜잭션 A가 이미 열려 있으므로 "합류"(join). 새 트랜잭션 아님.
  │       ├─ branchCurrencyRateRepository.findForUpdate(...)        line 46-47  → SELECT ... FOR UPDATE (환율/재고 행 락)
  │       ├─ rate.decreaseStock(request.amount())                   line 49
  │       ├─ lockTimeSlot(...) → ensureExists + lockForUpdate        line 52, 74-78  → SELECT ... FOR UPDATE (슬롯 행 락)
  │       ├─ slot.decreaseRemaining()                                line 52
  │       └─ reservationRepository.save(reservation)                line 64
  │        ▲
  │        └─ 메서드는 리턴하지만, 위에서 잡은 두 행 락은 트랜잭션 A가 살아있는 한 계속 유지됨
  │
  ├─ 6. reservation.assignLockedRate(lockedRate)                    line 98
  │
  ├─ 7. paymentService.createPaymentIntent(reservation)              line 102
  │        │
  │        ▼
  │     PaymentService.createPaymentIntent(reservation)              application/payment/PaymentService.java:48
  │       @Transactional (기본 REQUIRED, 클래스 기본값 readOnly=true를 메서드에서 덮어씀)
  │                                                                  ← 트랜잭션 A가 아직 열려 있으므로 이번에도 "합류". 새 트랜잭션 아님.
  │       ├─ Payment.initiate(...) + paymentRepository.save(payment) line 49-51
  │       ├─ StripeAmountConverter.toMinorUnits(...)                 line 53
  │       └─ paymentGateway.createIntent(...)                        line 58   ★ Stripe로 실제 HTTP 호출 (PaymentIntent 생성)
  │                                                                  ★ 이 호출이 끝날 때까지도 트랜잭션 A는 열려 있고,
  │                                                                    5번에서 잡은 재고/슬롯 행 락도 계속 유지된 상태
  │       └─ payment.attachIntent(result.paymentIntentId())          line 61
  │
  ├─ 8. Branch/BranchCurrencyRate 재조회 (응답 조립용)                line 107-109
  └─ 9. return toDetail(reservation, branch, rate, intent.clientSecret())  line 110
       │
       ▼
  [트랜잭션 A 커밋]  ← 여기서 비로소 재고/슬롯 행 락이 풀린다 (5번 이후 ~ 여기까지 계속 유지됐음)
       │
       ▼
[Client] ← ApiResponse{ reservationId, reservationNumber, clientSecret, ... }
```

**트랜잭션 경계 요약**: `createReservation`, `createHold`, `createPaymentIntent` 세 메서드 모두 `@Transactional`이 붙어 있지만 전부 기본 전파(`REQUIRED`)라서, 먼저 열린 트랜잭션 A 하나로 합쳐진다. 재고/슬롯 락은 `createHold` 시점(5번)에 걸려서 **Stripe HTTP 호출(7번)이 끝나고 응답 조립(8-9번)까지 마친 뒤** 트랜잭션 A가 커밋될 때 풀린다. 클래스를 `ReservationHoldService`/`PaymentService`로 분리한 것과 트랜잭션이 실제로 분리되는 것은 별개다 — 후자는 전파 타입(`REQUIRES_NEW` 등)으로만 결정된다.

---

## 2. 카드 결제 — 서버 API 아님

```
[Client]
  └─ Stripe Elements로 clientSecret을 사용해 stripe.confirmPayment() 호출
       (Stripe 서버와 브라우저 간 직접 통신 — 이 서버의 API를 거치지 않음)
```

---

## 3. 결제 승인/실패 — Stripe 웹훅 `POST /webhooks/stripe`

```
[Stripe]
  └─ POST /webhooks/stripe  (서버 대 서버, 서명 헤더 Stripe-Signature 포함)
       │
       ▼
StripeWebhookController.handle(payload, signatureHeader)            presentation/payment/StripeWebhookController.java:37
  ├─ Webhook.constructEvent(...)                                    line 41   서명 검증, 실패 시 400
  ├─ event.getDataObjectDeserializer()...                           line 51-60
  └─ switch(event.getType())                                        line 62
       ├─ "payment_intent.succeeded" → paymentService.handlePaymentSucceeded(paymentIntent.getId())   line 63
       │        │
       │        ▼
       │     PaymentService.handlePaymentSucceeded(pgPaymentIntentId)   application/payment/PaymentService.java:71
       │       @Transactional                                       ← 웹훅 요청 자체가 새 HTTP 요청이므로 여기서 트랜잭션 B가 새로 열림 (신청 흐름의 트랜잭션 A와는 무관)
       │       ├─ paymentRepository.findByPgPaymentIntentId(...)     line 72
       │       ├─ (멱등 체크: 이미 APPROVED면 no-op)                  line 77-79
       │       ├─ reservationRepository.findById(...)                line 81
       │       ├─ (PENDING_PAYMENT가 아니면: 수동 정산 로그만 남기고 종료)  line 89-100
       │       ├─ payment.markApproved(now)                          line 102
       │       ├─ reservation.confirmPayment(...)                    line 103  → 상태 PENDING_PAYMENT → RESERVED
       │       └─ reservation.issueQrToken(...)                      line 104
       │
       └─ "payment_intent.payment_failed" → paymentService.handlePaymentFailed(paymentIntent.getId())  line 64
                │
                ▼
             PaymentService.handlePaymentFailed(pgPaymentIntentId)   application/payment/PaymentService.java:112
               @Transactional                                       ← 역시 새 트랜잭션 (웹훅 요청 단위)
               └─ payment.markFailed(now)                            line 115   (예약 상태는 건드리지 않음 — 슬롯은 TTL까지 홀드 유지)
  └─ return 200 OK
```

---

## 참고: 재고/슬롯 복원(취소·거절·만료) 경로

신청 흐름과 별개로, 아래는 모두 `Propagation.REQUIRES_NEW`로 **의도적으로 트랜잭션을 분리**한 경로다(1번 흐름과 대조됨):

- `DELETE /reservations/{id}` → `ReservationController.cancelReservation` → `ReservationService.cancelReservation` → `self.doCancelReservation`(`@Transactional(REQUIRES_NEW)`, `ReservationService.java:149`)
- 지점 거절 → `ReservationService.rejectByBranch` → `self.doRejectByBranch`(`REQUIRES_NEW`, line 180)
- QR 리딤 → `ReservationService.redeem` → `self.doRedeem`(`REQUIRES_NEW`, line 217)

이 세 경로는 `self`(지연 주입된 자기 자신, `@Lazy`)를 통해 호출해서 프록시를 거치게 하고, `REQUIRES_NEW`로 낙관적 락 충돌 시 그 트랜잭션만 롤백되도록 설계돼 있다(discussion#41). **1번 신청 흐름에는 이 패턴이 없다** — `createHold`/`createPaymentIntent` 모두 기본 `REQUIRED`라서 `self`를 거치지도, 새 트랜잭션을 열지도 않는다.
