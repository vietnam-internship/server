package com.fptis.intern.server.domain.reservation;

import com.fptis.intern.server.global.base.BaseTimeEntity;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * lockedRate(예약 시점 고정 환율)는 기준 환율(Currency 도메인)이 없어 항상 null이다 — #26에서 연동 예정.
 * qrToken은 상세 재조회(GET /reservations/{id})에서도 다시 내려줘야 해서 RefreshToken처럼
 * 해시만 저장할 수 없고 평문으로 저장한다 — 대신 상태 전이 시 즉시 null로 지워 재사용을 막는다.
 * discussion#16(방안 2)에 따라 생성 시점엔 결제 전 홀드(PENDING_PAYMENT)만 잡고, QR과 픽업 TTL은
 * 결제 승인({@link #confirmPayment})이 끝난 뒤에만 발급/기산한다 — 결제 전 QR이 존재해선 안 된다.
 */
@Getter
@Entity
@Table(name = "reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    private static final int PICKUP_HOLD_DURATION_HOURS = 2;
    private static final int PAYMENT_HOLD_DURATION_MINUTES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(nullable = false)
    private double amount;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "pickup_time", nullable = false)
    private LocalTime pickupTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "reservation_number", unique = true, length = 30)
    private String reservationNumber;

    @Column(name = "locked_rate")
    private Double lockedRate;

    /**
     * 결제 대기(PENDING_PAYMENT) 홀드의 TTL(5분) — 이 시간 안에 결제를 승인받지 못하면 EXPIRED로 풀린다.
     */
    @Column(name = "payment_expires_at")
    private LocalDateTime paymentExpiresAt;

    /**
     * 픽업 마감 시각(2시간) — 결제 승인({@link #confirmPayment}) 시점부터 기산하며, 그 전까지는 null이다.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "qr_token", length = 64)
    private String qrToken;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    /**
     * 픽업 홀드가 만료돼 시스템이 자동 취소했는지 — 노쇼 이력 집계(PRD §19.2)에만 쓰이는 내부 플래그다.
     * 결제 전 홀드(PENDING_PAYMENT)가 TTL을 넘겨 풀리는 경우는 노쇼가 아니라 EXPIRED 상태로 별도
     * 분리한다 — 결제까지 가지 않은 유령 홀드를 노쇼 이력에 섞으면 무고한 사용자를 차단할 수 있다.
     */
    @Column(name = "auto_expired", nullable = false)
    private boolean autoExpired;

    @Builder
    private Reservation(Long userId, Long branchId, String currencyCode, double amount,
                         LocalDate pickupDate, LocalTime pickupTime, LocalDateTime now) {
        this.userId = userId;
        this.branchId = branchId;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.pickupDate = pickupDate;
        this.pickupTime = pickupTime;
        this.status = ReservationStatus.PENDING_PAYMENT;
        this.lockedRate = null;
        this.paymentExpiresAt = now.plusMinutes(PAYMENT_HOLD_DURATION_MINUTES);
        this.autoExpired = false;
    }

    public void assignReservationNumber(String reservationNumber) {
        this.reservationNumber = reservationNumber;
    }

    public void issueQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public boolean isPaymentExpired(LocalDateTime now) {
        return status == ReservationStatus.PENDING_PAYMENT && paymentExpiresAt != null && paymentExpiresAt.isBefore(now);
    }

    public boolean isExpired(LocalDateTime now) {
        return status == ReservationStatus.RESERVED && expiresAt != null && expiresAt.isBefore(now);
    }

    /**
     * 결제 승인 성공 시 호출한다 — 픽업 TTL을 이 시점부터 새로 기산한다. QR 발급은 호출자가
     * {@link #issueQrToken}으로 이어서 처리한다(결제 승인과 QR 발급 책임을 분리).
     */
    public void confirmPayment(LocalDateTime now) {
        if (status != ReservationStatus.PENDING_PAYMENT) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_NOT_PENDING);
        }
        this.status = ReservationStatus.RESERVED;
        this.expiresAt = now.plusHours(PICKUP_HOLD_DURATION_HOURS);
    }

    /**
     * 결제 TTL을 넘긴 유령 홀드를 풀 때 호출한다 — 재고/슬롯 복원은 호출자(ReservationService) 책임이다.
     */
    public void expireHold() {
        if (status != ReservationStatus.PENDING_PAYMENT) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_NOT_PENDING);
        }
        this.status = ReservationStatus.EXPIRED;
    }

    public void cancel(boolean autoExpired) {
        if (status == ReservationStatus.CANCELLED) {
            throw new BusinessException(BusinessErrorCode.ALREADY_CANCELLED);
        }
        if (status == ReservationStatus.COMPLETED) {
            throw new BusinessException(BusinessErrorCode.ALREADY_COMPLETED);
        }
        if (status == ReservationStatus.EXPIRED) {
            throw new BusinessException(BusinessErrorCode.RESERVATION_ALREADY_EXPIRED);
        }
        this.status = ReservationStatus.CANCELLED;
        this.autoExpired = autoExpired;
        this.qrToken = null;
    }

    public void complete(LocalDateTime pickedUpAt) {
        this.status = ReservationStatus.COMPLETED;
        this.pickedUpAt = pickedUpAt;
        this.qrToken = null;
    }
}
