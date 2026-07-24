package com.fptis.intern.server.domain.payment;

import com.fptis.intern.server.global.base.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * discussion#16(방안 2)에서 결정한 대로 Reservation의 재고 홀드와 분리된 결제 기록이다.
 * idempotencyKey는 Reservation.reservationNumber와 1:1로 고정해 Stripe PaymentIntent 생성 재시도가
 * 중복 결제를 만들지 않게 한다(Idempotency-Key 헤더). pgPaymentIntentId(Stripe의 `pi_...`)는
 * PaymentIntent 생성이 성공한 뒤에만 채워지며, 실제 승인 여부는 이 값을 키로 웹훅에서 확정된다.
 */
@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "pg_payment_intent_id", unique = true, length = 64)
    private String pgPaymentIntentId;

    @Column(nullable = false)
    private double amount;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Builder
    private Payment(Long reservationId, PaymentProvider provider, String idempotencyKey, double amount,
                     String currencyCode, LocalDateTime requestedAt) {
        this.reservationId = reservationId;
        this.provider = provider;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.status = PaymentStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    public static Payment initiate(Long reservationId, PaymentProvider provider, String idempotencyKey,
                                    double amount, String currencyCode, LocalDateTime now) {
        return Payment.builder()
                .reservationId(reservationId)
                .provider(provider)
                .idempotencyKey(idempotencyKey)
                .amount(amount)
                .currencyCode(currencyCode)
                .requestedAt(now)
                .build();
    }

    public void attachIntent(String pgPaymentIntentId) {
        this.pgPaymentIntentId = pgPaymentIntentId;
    }

    public void markApproved(LocalDateTime now) {
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = now;
    }

    public void markFailed(LocalDateTime now) {
        this.status = PaymentStatus.FAILED;
        this.failedAt = now;
    }
}
