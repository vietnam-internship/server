# 결제 페이지(PaymentPage) 로컬 테스트 가이드

신청(예약 생성) 프론트 플로우가 아직 실제 DB 데이터가 아니라 목업 위에서 돌기 때문에,
`ReviewReservationPage` 화면을 눌러서는 결제 페이지까지 갈 수 없다. 대신 `curl`로 백엔드
API를 직접 호출해 `clientSecret`을 받은 뒤, 결제 페이지 URL에 쿼리파라미터로 붙여 직접
접속해서 테스트한다.

## 0. 사전 준비

- `.env.local`(서버 루트)에 `GOOGLE_CLIENT_ID`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`,
  `JWT_SECRET` 등이 채워져 있어야 함. `SPRING_PORT=3005`, `DEV_AUTH_ENABLED=true` 확인.
- `client/.env`의 `VITE_STRIPE_PUBLISHABLE_KEY`를 Stripe 대시보드 > Developers > API keys에서
  `STRIPE_SECRET_KEY`와 **같은 테스트 계정**의 `pk_test_...` 값으로 교체.
- `client/.env`의 `VITE_API_BASE_URL=http://localhost:3005` 확인 (백엔드 로컬 포트와 일치해야 함).

## 1. 서버 실행

```bash
# 백엔드 (repo root)
./gradlew bootRun

# 프론트 (client/)
pnpm dev
```

## 2. 관리자 토큰 발급 (지점 생성용)

`DEV_AUTH_ENABLED=true`일 때만 열리는 `/dev/auth/token`으로 Google 로그인 없이 바로
JWT를 받는다 (로컬 전용, prod/staging엔 이 컨트롤러 자체가 없음).

```bash
curl -s -X POST http://localhost:3005/dev/auth/token \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.local","role":"ADMIN"}' | tee /tmp/admin-token.json

ADMIN_TOKEN=$(jq -r .accessToken /tmp/admin-token.json)
```

## 3. 테스트용 지점 생성 + 재고 세팅

```bash
curl -s -X POST http://localhost:3005/admin/branches \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "테스트 지점",
    "address": "서울시 강남구 테스트로 1",
    "latitude": 37.5,
    "longitude": 127.0,
    "phone": "02-000-0000",
    "businessHours": "09:00-18:00",
    "pickupLocationDetail": "1층 로비",
    "timeSlotCapacity": 5,
    "supportedCurrencies": ["USD"]
  }' | tee /tmp/branch.json

BRANCH_ID=$(jq -r .id /tmp/branch.json)

# 재고(reservationOnlyStock)를 안 잡아두면 STOCK_EXCEEDED로 예약 생성이 막힌다.
curl -s -X PATCH "http://localhost:3005/admin/branches/$BRANCH_ID/rate" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currencyCode":"USD","preferentialRate":1350.0,"reservationOnlyStock":1000}'
```

## 4. 일반 유저 토큰 발급 + 예약 생성

```bash
curl -s -X POST http://localhost:3005/dev/auth/token \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.local"}' | tee /tmp/user-token.json

USER_TOKEN=$(jq -r .accessToken /tmp/user-token.json)

# pickupDate는 오늘 이후 날짜로, pickupTime은 HH:00 또는 HH:30만 허용됨.
curl -s -X POST http://localhost:3005/reservations \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"currencyCode\": \"USD\",
    \"branchId\": $BRANCH_ID,
    \"amount\": 100,
    \"pickupDate\": \"2026-08-01\",
    \"pickupTime\": \"14:00\"
  }" | tee /tmp/reservation.json

RESERVATION_ID=$(jq -r .data.id /tmp/reservation.json)
CLIENT_SECRET=$(jq -r .data.paymentClientSecret /tmp/reservation.json)
echo "http://localhost:5173/reserve/$RESERVATION_ID/payment?clientSecret=$CLIENT_SECRET"
```

> `paymentClientSecret`은 예약 **생성** 응답에만 담긴다 (DB엔 저장 안 함, 재조회 시 항상 null).
> 즉 이 curl을 다시 못 쓰니 값을 바로 복사해두거나 위 스크립트를 한 번에 실행할 것.

## 5. 결제 페이지에서 결제

1. 마지막 줄에 출력된 URL을 브라우저에 직접 입력해서 접속 (Google 로그인은 이미 되어 있어야 함 —
   `requireAuth` 라우트라 `travelx.accessToken`이 localStorage에 있어야 페이지가 리다이렉트 안 됨).
2. Stripe 테스트 카드로 결제:
   - 카드번호 `4242 4242 4242 4242`
   - 유효기간: 미래 아무 날짜, CVC: 아무 3자리, 우편번호: 아무 값
3. "Pay now" 클릭 → 에러 없이 `/reserve/{id}/complete`로 이동하면 Stripe 쪽 결제 자체는 성공.

## 6. (선택) 웹훅까지 확인하려면 — QR 발급 여부

결제 승인 후 예약 상태가 `RESERVED`로 바뀌고 QR이 발급되는 건 Stripe **웹훅**
(`payment_intent.succeeded`)이 백엔드에 도착해야 일어난다. 로컬은 Stripe가 직접 못 때리므로
Stripe CLI로 포워딩해야 한다.

```bash
stripe listen --forward-to localhost:3005/webhooks/stripe
```

이 명령이 출력하는 `whsec_...` 값을 `.env.local`의 `STRIPE_WEBHOOK_SECRET`에 넣고 백엔드를
재시작해야 서명 검증이 통과한다. 이후 결제하면:

```bash
curl -s http://localhost:3005/reservations/$RESERVATION_ID \
  -H "Authorization: Bearer $USER_TOKEN" | jq .data.status
# "RESERVED"가 나오면 웹훅까지 정상 처리된 것
```

## 자주 막히는 지점

| 증상 | 원인 |
|---|---|
| 결제 페이지가 `/reserve/{id}`로 튕김 | `clientSecret`이 비어있음 — `?clientSecret=` 값 또는 URL 인코딩 확인 |
| 결제 페이지가 `/login`으로 튕김 | 브라우저에 로그인 세션이 없음 — 먼저 구글 로그인 한 번 하고 진행 |
| `PaymentElement`가 안 뜨고 콘솔에 Stripe 에러 | `VITE_STRIPE_PUBLISHABLE_KEY`가 placeholder(`pk_test_replace_me`)로 남아있음 |
| 예약 생성이 `STOCK_EXCEEDED`로 실패 | 3단계 재고 세팅(`PATCH .../rate`)을 안 함 |
| 결제는 성공했는데 예약 상태가 계속 `PENDING_PAYMENT` | 웹훅이 안 옴 — 6번(Stripe CLI 포워딩) 확인 |
