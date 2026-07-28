# 로컬 E2E: 예약(Hold→Pay) + Stripe 결제 흐름

프론트 없이 curl + Stripe CLI만으로 discussion#16(방안 2) 전체 흐름을
로컬에서 검증하는 순서다. 요약: **홀드 생성 → Stripe로 직접 결제 승인 → 웹훅 수신 →
예약 RESERVED 전환 확인 → 픽업(redeem)**.

## 0. 준비물

- 로컬 MySQL(`travelx` DB) 기동, `.env`/`secret.env`에 `DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET` 등 기존 값 설정 완료
- [Stripe CLI](https://docs.stripe.com/stripe-cli) 설치 후 `stripe login` (테스트 모드 계정)
- `.env`에 아래 두 값 추가:
  ```
  STRIPE_SECRET_KEY=sk_test_...       # Stripe 대시보드 > Developers > API keys (test mode)
  STRIPE_WEBHOOK_SECRET=whsec_...     # 아래 1단계에서 stripe listen이 출력하는 값
  ```
- `.env`에 `DEV_AUTH_ENABLED=true` 추가 (4단계의 `/dev/auth/token` 엔드포인트 활성화용, 로컬 전용)

## 1. 웹훅 포워딩 먼저 켜기 (별도 터미널, 서버 기동보다 먼저)

```bash
stripe listen --forward-to localhost:8080/webhooks/stripe
```

출력되는 `whsec_...`를 `.env`의 `STRIPE_WEBHOOK_SECRET`에 넣는다. 이 터미널은 이후 계속 열어둔다 —
Stripe에서 발생하는 이벤트(`payment_intent.succeeded` 등)를 실시간으로 로컬 서버에 전달해준다.

## 2. 서버 기동

```bash
./gradlew bootRun
```

Flyway가 자동으로 `V1~V9` 마이그레이션을 적용한다(신규 `payments` 테이블 포함).

## 3. 테스트 데이터 시딩 (SQL 직접 insert)

유저는 4단계의 `/dev/auth/token`이 대신 만들어주므로, 여기서는 지점 어드민 API를 다 타는 대신
branch/rate만 SQL로 바로 넣는다.

```sql
INSERT INTO branches (name, address, latitude, longitude, phone, business_hours,
                       time_slot_capacity, active, created_at, updated_at)
VALUES ('명동 환전센터', '서울 중구 명동길 1', 37.5665, 126.9780, '02-123-4567',
        '평일 09:00-18:00', 5, 1, NOW(6), NOW(6));

-- branch_id는 위에서 방금 만든 branches.id로 바꿔 넣는다
INSERT INTO branch_currency_rates (branch_id, currency_code, preferential_rate,
                                    reservation_only_stock, created_at, updated_at)
VALUES (1, 'USD', 0.5, 1000, NOW(6), NOW(6));
```

```sql
SELECT id FROM branches ORDER BY id DESC LIMIT 1;       -- branchId 확인
```

## 4. JWT 발급 — `/dev/auth/token` (로컬 전용)

`.env`에 `DEV_AUTH_ENABLED=true`를 추가하고 서버를 (재)기동하면 `POST /dev/auth/token`
엔드포인트가 열린다(`travelx.dev.auth.enabled=true`가 아니면 빈 자체가 등록되지 않는 로컬
전용 API). email로 유저를 찾아 없으면 role을 지정해 새로 만들고 — phone 인증도 자동으로
완료 처리되므로 3단계의 SQL 시딩 없이 바로 예약까지 테스트할 수 있다 — access token을 바로 내려준다.

```bash
curl -s -X POST http://localhost:8080/dev/auth/token \
  -H "Content-Type: application/json" \
  -d '{"email": "e2e@test.local", "role": "USER"}' | tee user_token.json

curl -s -X POST http://localhost:8080/dev/auth/token \
  -H "Content-Type: application/json" \
  -d '{"email": "e2e-admin@test.local", "role": "ADMIN"}' | tee admin_token.json
```

```bash
export TOKEN=$(jq -r '.accessToken' user_token.json)
export ADMIN_TOKEN=$(jq -r '.accessToken' admin_token.json)
```

이미 존재하는 email이면 role 파라미터는 무시되고 기존 유저 그대로 토큰만 재발급된다 — role을
바꾸고 싶으면 다른 email을 쓰거나 DB에서 직접 고친다. (3단계의 SQL 시딩은 branch/rate처럼
이 엔드포인트가 대신해줄 수 없는 데이터에만 그대로 필요하다.)

## 5. 예약 홀드 생성 — `POST /reservations`

```bash
curl -s -X POST http://localhost:8080/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "currencyCode": "USD",
    "branchId": 1,
    "amount": 500,
    "pickupDate": "2026-07-25",
    "pickupTime": "10:30"
  }' | tee create.json
```

- `pickupTime`은 `HH:00` 또는 `HH:30`만 허용(정규식 검증).
- 응답에서 확인할 것: `status: "PENDING_PAYMENT"`, `qrPayload: null`, `paymentExpiresAt` 채워짐,
  **`paymentClientSecret`이 non-null**(`pi_xxx_secret_yyy` 형태). 이 안의 `pi_xxx` 부분이
  Stripe PaymentIntent ID다.

```bash
export RESERVATION_ID=$(jq -r '.data.id' create.json)
export PI_ID=$(jq -r '.data.paymentClientSecret' create.json | cut -d'_' -f1-2)
```
(`client_secret`은 `pi_XXXX_secret_YYYY` 형식이라 `_secret_` 앞부분이 PaymentIntent ID다. `jq`가
없으면 `payments` 테이블에서 바로 확인해도 된다: `SELECT pg_payment_intent_id FROM payments WHERE reservation_id = <id>;`)

## 6. Stripe에 직접 결제 승인 요청 (프론트/Stripe.js 없이)

프론트가 없으므로 Stripe 테스트용 PaymentMethod 토큰으로 서버 대신 우리가 직접 confirm을 호출한다.
이게 실제로는 Stripe.js가 브라우저에서 하는 일이다 — **우리 서버는 전혀 관여하지 않는다.**

**성공 케이스:**
```bash
curl -s https://api.stripe.com/v1/payment_intents/$PI_ID/confirm \
  -u $STRIPE_SECRET_KEY: \
  -d payment_method=pm_card_visa \
  -d "automatic_payment_methods[allow_redirects]=never"
```

**실패(카드 거절) 케이스를 보고 싶으면:**
```bash
curl -s https://api.stripe.com/v1/payment_intents/$PI_ID/confirm \
  -u $STRIPE_SECRET_KEY: \
  -d payment_method=pm_card_chargeDeclined \
  -d "automatic_payment_methods[allow_redirects]=never"
```

이 호출이 성공/실패하면 Stripe가 `payment_intent.succeeded` / `payment_intent.payment_failed`
웹훅을 쏘고, 1단계에서 켜둔 `stripe listen`이 그걸 `localhost:8080/webhooks/stripe`로 그대로
전달한다. `stripe listen` 터미널에 이벤트가 찍히는지 확인한다.

## 7. 예약 상태 확인 — `GET /reservations/{id}`

```bash
curl -s http://localhost:8080/reservations/$RESERVATION_ID \
  -H "Authorization: Bearer $TOKEN" | jq .
```

- 성공 케이스 확인: `status: "RESERVED"`, `qrPayload` 채워짐, `expiresAt`(픽업 마감, +2h) 채워짐,
  `paymentClientSecret: null`(DB에 저장 안 하므로 재조회 시 항상 null).
- 실패 케이스 확인: `status`는 그대로 `"PENDING_PAYMENT"` 유지(카드 거절은 예약을 죽이지 않는다) —
  `payments` 테이블에서 `status = 'FAILED'`인지 확인하고, 5단계로 돌아가 다른 카드로 재시도 가능.

```bash
export QR=$(curl -s http://localhost:8080/reservations/$RESERVATION_ID -H "Authorization: Bearer $TOKEN" | jq -r '.data.qrPayload')
```

## 8. 픽업 처리(redeem) — 관리자 권한, `POST /branches/{branchId}/reservations/{id}/redeem`

```bash
curl -s -X POST http://localhost:8080/branches/1/reservations/$RESERVATION_ID/redeem \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"qrToken\": \"$QR\", \"idVerified\": true}" | jq .
```

응답 `status: "COMPLETED"`, `pickedUpAt` 채워짐이면 정상.

## 9. (선택) 취소 흐름

```bash
curl -s -X DELETE http://localhost:8080/reservations/$RESERVATION_ID \
  -H "Authorization: Bearer $TOKEN"
```
`PENDING_PAYMENT`/`RESERVED` 둘 다 취소 가능, 재고/슬롯이 즉시 복원되는지
`branch_currency_rates.reservation_only_stock` / `branch_time_slots.remaining`으로 확인.

## 10. (선택) TTL 만료 스윕 빠르게 확인하기

스윕은 60초마다 도는데, 5분 결제 TTL을 다 기다리기 싫으면 DB에서 직접 만료시킨다:

```sql
UPDATE reservations SET payment_expires_at = NOW(6) - INTERVAL 1 MINUTE WHERE id = <id>;
```

최대 60초 뒤 `GET /reservations/{id}`로 `status: "EXPIRED"` 전환 + 재고/슬롯 복원을 확인한다.

**레이스 케이스(뒤늦은 웹훅)**도 이 방법으로 재현 가능: 위처럼 먼저 강제 만료시킨 뒤 6단계의
confirm을 호출하면, `reservations.status`는 `EXPIRED`로 그대로 남고 `payments.status`만
`APPROVED`가 되는 걸 확인할 수 있다 — 서버 로그에 `[PaymentService] 홀드가 이미 종료된 뒤...`
경고가 찍히는지도 같이 확인한다(운영에서 수동 정산 대상으로 표시되는 지점).

## 트러블슈팅

- **웹훅이 안 옴**: `stripe listen`을 서버보다 먼저/계속 띄워뒀는지, `STRIPE_WEBHOOK_SECRET`이
  최신 `whsec_...`로 반영된 뒤 서버를 재시작했는지 확인.
- **confirm 호출이 `requires_action`으로 멈춤**: `automatic_payment_methods[allow_redirects]=never`
  옵션을 빼먹지 않았는지 확인(카드가 아닌 리다이렉트형 결제수단으로 잘못 라우팅되는 걸 막는 옵션).
- **401/403**: JWT의 `role` 클레임과 호출하는 엔드포인트의 `@RequireAuth(roles=...)`가 맞는지
  확인(redeem은 ADMIN 전용).
