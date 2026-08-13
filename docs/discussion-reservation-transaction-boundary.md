### 배경

`docs/reservation-payment-call-flow.md`에서 확인했듯, `POST /reservations`(`ReservationService.createReservation`)는 검증 → 재고/슬롯 홀드 → Stripe PaymentIntent 생성 → 응답 조립까지 전부 트랜잭션 하나(A)에 물려 있습니다. `createHold`/`createPaymentIntent`가 별도 클래스에 `@Transactional`로 나뉘어 있어도, 기본 전파(`REQUIRED`)라서 먼저 열린 트랜잭션 A에 그대로 합류합니다 — 클래스 분리와 트랜잭션 분리는 별개입니다.

그 결과 두 종류의 락이 필요 이상으로 오래 유지됩니다.

- **재고·슬롯 락**: `createHold`에서 잡은 `BranchCurrencyRate`/`BranchTimeSlot` 행 락이, 뒤이은 Stripe HTTP 호출(`PaymentService.createPaymentIntent` → `paymentGateway.createIntent`)과 응답 조립까지 끝나야 풀립니다. discussion#16이 "방안 1(결합)의 핵심 문제"로 지목했던 것과 동일한 모양입니다.
- **유저 행 락**: 최근 30일 내 노쇼 1~2회인 사용자는 `assertNoShowLimitNotExceeded`(`ReservationService.java:328-342`)에서 `userRepository.findForUpdate(userId)`로 자기 유저 행에 락을 겁니다. 이 락도 트랜잭션 A가 끝날 때까지, 즉 Stripe 호출이 끝날 때까지 유지됩니다.

이걸 다시 설계할 때 부딪히는 제약은 세 가지입니다.

1. **"먼저 실행돼야 한다"와 "같은 트랜잭션에 있어야 한다"는 다른 요구입니다.** 예를 들어 `createHold`(재고/슬롯 락)는 `createPaymentIntent`(Stripe 호출) 앞에 실행돼야 하지만, 이건 "예약 PK가 있어야 Payment를 만들 수 있다"는 **데이터 의존성**이지 "같은 트랜잭션이어야 락이 의미 있다"는 **락 의존성**이 아닙니다. 반대로 노쇼 체크의 유저 락은 "체크 → (그 락을 쥔 채로) 홀드 insert"까지가 한 트랜잭션이어야 TOCTOU(check-then-act 레이스)가 안 생기는, 진짜 락 의존성입니다. 이 둘을 구분해야 어디를 쪼갤 수 있는지 알 수 있습니다.
2. **Spring Data JPA repository 메서드는 그 자체로 개별 트랜잭션을 겁니다.** `SimpleJpaRepository`의 각 메서드에 자체 `@Transactional`이 붙어 있어서, 바깥에 명시적으로 열린 트랜잭션이 없으면 `repository.findById(...)` 같은 호출 하나하나가 자기 트랜잭션을 열고 즉시 커밋합니다. 순수 조회 단계는 이 프레임워크 기본 동작에 맡기면 되고, 굳이 서비스 메서드 전체를 하나의 `@Transactional`로 감쌀 필요가 없습니다.
3. **단, 변경 감지(dirty checking)가 필요한 구간은 예외입니다.** `createHold`의 `rate.decreaseStock(...)`/`slot.decreaseRemaining(...)`은 영속 상태 엔티티를 필드 변경만으로 갱신하고 flush 시점에 UPDATE가 나가는 방식이라, 명시적으로 열린 `@Transactional` 경계 안에 있어야만 동작합니다. 1번 제약과 헷갈리면 "JPA가 알아서 트랜잭션을 걸어주니 명시적 경계를 다 빼도 된다"는 잘못된 결론에 이릅니다 — 그건 순수 조회에만 해당합니다.

참고로 확인한 것: `Reservation` 엔티티에는 `@ManyToOne`/`@OneToOne` 같은 지연 로딩 연관관계가 없습니다(FK 자체가 V15에서 전부 제거됨, `reservation-payment-review.md` 1.3). 그래서 응답 조립 단계를 트랜잭션 밖으로 빼도 `LazyInitializationException` 걱정은 없습니다.

---

### 단계별 의존성 분류

`createReservation`의 각 단계를 "순서 의존"과 "락(트랜잭션) 의존"으로 나누면 다음과 같습니다.

| 단계 | 내용 | 이 앞 단계와의 관계 | 같은 트랜잭션이어야 하는가 |
|---|---|---|---|
| 0-a | `userRepository.findById` (인증) | 독립 | 아니오 — 단독 조회 |
| 0-b | `assertNoConcurrentPendingPayment` (COUNT) | 0-a와 독립 (순서 무관) | 현재는 락이 아예 없음 (별도 결함, 아래 참고) |
| 0-c | `assertNoShowLimitNotExceeded` | 0-a/0-b와 독립 | **노쇼 1~2회 분기만 예외** — 유저 락을 Phase 1까지 끌고 가야 TOCTOU가 안 남음 |
| 0-d | branch/currency/rate 조회 + `assertAmountWithinLimits` | 0-a/0-b/0-c와 독립, 서로 간엔 조회 순서 의존(currency·rate 먼저) | 아니오 — 순수 조회+계산 |
| 1 | `createHold` (재고·슬롯 비관적 락 + Reservation insert) | 0-a~0-d가 통과해야 실행할 이유가 있음(순서 의존) | **예** — dirty checking 구간, 명시적 트랜잭션 필수 |
| 2 | `createPaymentIntent` (Payment insert + Stripe 호출) | 1의 결과(Reservation PK)가 있어야 함(데이터 의존) | 아니오 — 1의 락을 이어받을 필요 없음, Payment insert만 자체 트랜잭션이면 충분 |
| 3 | 응답 조립(`toDetail`) | 1, 2의 결과가 있어야 함(데이터 의존) | 아니오 — 순수 조회 |

핵심은 1→2, 2→3 모두 **데이터 의존**이지 **락 의존**이 아니라는 것입니다. 반면 0-c(노쇼, 조건부)만 1과 락을 공유해야 하는 예외입니다.

---

### 해결방안 — 트랜잭션 경계를 단계별로 재설계

#### 방안 A: 4단계로 분리 (제안)

```
Phase 0 (무경계 또는 readOnly)  — 인증·노쇼(예외 제외)·펜딩결제·금액한도 검증
   │  (순서 의존만, 락 공유 없음)
   ▼
Phase 1 (@Transactional, 단독)  — createHold: 재고/슬롯 락 획득 → 즉시 차감 → Reservation insert
   │  (데이터 의존: Reservation PK)
   ▼
Phase 2 (@Transactional(REQUIRES_NEW))  — createPaymentIntent: Payment insert + Stripe 호출
   │  (데이터 의존: PaymentIntentResult)
   ▼
Phase 3 (무경계 또는 readOnly)  — 응답 조립
```

Phase 2에 `REQUIRES_NEW`를 명시하는 이유: `createReservation`을 감싸는 외부 `@Transactional`을 제거하면 오늘 시점엔 `REQUIRES_NEW` 없이도 새 트랜잭션이 열리지만, 이 서비스가 나중에 다른 트랜잭션 컨텍스트(예: 배치, 다른 서비스의 합성 호출)에서 불려도 락을 이어받지 않는다는 걸 코드로 보장하기 위해서입니다. `ReservationService`의 취소/거절/리딤 경로(`doCancelReservation`/`doRejectByBranch`/`doRedeem`, 전부 `REQUIRES_NEW`)가 이미 이 패턴을 쓰고 있어서, 이번 제안은 새 패턴이 아니라 **기존에 팀이 이미 채택한 패턴을 신청 흐름에도 일관되게 적용**하는 것에 가깝습니다.

Phase 1은 지금 구조 그대로 유지합니다 — `ReservationHoldService.createHold`는 이미 별도 클래스+`@Transactional`이라, 외부 트랜잭션만 걷어내면 자동으로 자기 트랜잭션이 됩니다.

---

### 남는 갈림길 — 유저 단위 불변식을 어디서 강제할 것인가

Phase 0/1을 완전히 분리하면 한 가지가 깨집니다: 노쇼 1~2회 사용자의 "유저 락 → 활성 예약 카운트 확인 → (그 락을 쥔 채) 홀드 insert" TOCTOU 방지가, 유저 락이 Phase 0에서 이미 풀려버리면 무력화됩니다. 그리고 이건 사실 `assertNoConcurrentPendingPayment`(펜딩결제 1건 제한)에도 똑같이 해당하는 문제입니다 — 다만 그쪽은 애초에 락 자체가 없어서(`reservation-payment-review.md` ③) 오늘도 이미 깨져 있는 상태입니다. 두 검증 다 "이 유저에 대해 어떤 조건이 성립하는 동안에만 새 예약을 허용한다"는 같은 모양의 유저 단위 불변식이라, 해결 방향도 같이 고민하는 게 맞습니다.

#### 방안 1: 유저 락을 Phase 1까지 끌고 간다

노쇼 체크 방식을 펜딩결제 체크에도 그대로 확장 — `userRepository.findForUpdate(userId)`로 유저 행을 잠근 뒤 두 조건(활성 RESERVED 없음, 펜딩 PENDING_PAYMENT 없음)을 함께 확인하고, 그 락을 `createHold`(Phase 1)까지 유지합니다.

- 장점: 지금 있는 패턴 그대로 확장이라 검증된 방식, TOCTOU 완전 차단.
- 단점: Phase 0과 Phase 1이 다시 하나의 트랜잭션으로 묶여야 함 — 이번 분리 제안의 이득 일부(유저 락이 Stripe 호출까지 안 물리게 하는 것)를 도로 반납하는 셈. 단, 재고/슬롯 락과 달리 유저 락은 "같은 유저의 동시 요청"만 직렬화하므로 영향 범위가 좁습니다.

#### 방안 2: DB 제약으로 락 없이 강제한다

"이 유저에게 활성 RESERVED/PENDING_PAYMENT가 있으면 새 행을 못 만든다"를 UNIQUE 제약 + INSERT 실패로 강제합니다. MySQL 8.4(`docker-compose.yml`)는 부분 유니크 인덱스(partial unique index)를 지원하지 않으므로, 생성 컬럼(generated column)으로 우회해야 합니다.

```sql
ALTER TABLE reservations
  ADD COLUMN pending_gate_user_id BIGINT
    GENERATED ALWAYS AS (CASE WHEN status = 'PENDING_PAYMENT' THEN user_id END) STORED,
  ADD UNIQUE KEY uq_pending_gate (pending_gate_user_id);
```

`status <> 'PENDING_PAYMENT'`인 행은 생성 컬럼이 NULL이 되고, UNIQUE 인덱스는 NULL끼리 충돌시키지 않으므로 다른 상태의 행은 몇 개든 공존합니다. 같은 유저가 `PENDING_PAYMENT`를 두 번 가지려 하면 두 번째 INSERT가 그대로 실패합니다. 노쇼 쪽 "활성 RESERVED 1건 제한"도 같은 기법으로 만들 수 있습니다.

- 장점: Phase 0을 완전히 분리 가능 — 락을 아예 안 씀. 지금 깨져 있는 펜딩결제 레이스(③)도 같이 고쳐짐.
- 단점: 마이그레이션 필요, "왜 실패했는지"를 유니크 제약 위반 예외에서 우리 에러 코드로 다시 매핑하는 예외 처리 계층이 하나 더 생김. 락 기반보다 실패 시점의 에러 메시지가 한 단계 간접적입니다.

---

### 비교표

| 관점 | 방안 1 (유저 락 유지) | 방안 2 (DB 제약) |
|---|---|---|
| Phase 0/1 완전 분리 | ❌ 노쇼 분기는 여전히 결합 | ✅ 완전 분리 가능 |
| 구현 난이도 | ✅ 기존 패턴 확장 | ❌ 마이그레이션 + 예외 매핑 필요 |
| 펜딩결제 레이스(③) 동시 해결 | 가능 (같은 방식으로 확장 시) | ✅ 자연스럽게 같이 해결됨 |
| 실패 원인 진단 | ✅ 애플리케이션 레벨에서 명확 | ❌ DB 제약 위반을 다시 해석해야 함 |
| 영향 범위 | 좁음 (같은 유저 요청끼리만 직렬화) | 없음 (락 자체가 없음) |

---

### 의견 수립

Phase 0(검증)~Phase 3(응답 조립) 분리, Phase 2에 `REQUIRES_NEW` 명시는 기존 취소/거절/리딤 경로와 같은 패턴이라 이번에 같이 적용하는 쪽을 제안합니다.

---

### 의견 요청

1. 유저 단위 불변식(노쇼·펜딩결제 동시 제한)을 방안 1(락 유지)로 갈지 방안 2(DB 제약)로 갈지 — 이건 이번 리팩터링 범위를 트랜잭션 경계 정리로 좁게 볼지, 펜딩결제 레이스(③)까지 함께 고칠지와도 연결된 판단이라 함께 결정이 필요합니다.
2. Phase 0→1 사이에는 방안 1을 쓰더라도 여전히 "검증 통과 후 ~ 유저 락 획득 전" 짧은 틈이 남습니다(노쇼 분기가 아닌 일반 경로는 애초에 락이 없으므로). 이 정도 TOCTOU 창은 현재 트래픽 규모에서 감수할 만한지 확인이 필요합니다.
