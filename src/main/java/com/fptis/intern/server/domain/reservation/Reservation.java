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
 * qrToken은 상세 재조회(GET /reservations/{id})에서도 다시 내려줘야 해서 RefreshToken처럼
 * 해시만 저장할 수 없고 평문으로 저장한다 — 대신 상태 전이 시 즉시 null로 지워 재사용을 막는다.
 */
@Getter
@Entity
@Table(name = "reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    private static final int HOLD_DURATION_HOURS = 2;

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

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "qr_token", length = 64)
    private String qrToken;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    /**
     * 재고 홀드가 만료돼 시스템이 자동 취소했는지 — 노쇼 이력 집계(PRD §19.2)에만 쓰이는
     * 내부 플래그다. API의 ReservationStatus는 스펙대로 RESERVED/COMPLETED/CANCELLED 3개뿐이다.
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
        this.status = ReservationStatus.RESERVED;
        this.lockedRate = null;
        this.expiresAt = now.plusHours(HOLD_DURATION_HOURS);
        this.autoExpired = false;
    }

    public void assignReservationNumber(String reservationNumber) {
        this.reservationNumber = reservationNumber;
    }

    public void issueQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    /**
     * 예약 시점의 지점별 최종 환율(BranchCurrencyRate.finalRate)을 고정한다 — 이후 우대율/기준 환율이
     * 바뀌어도 이 예약의 확정 금액(amountFrom) 계산은 이 값 그대로 유지된다.
     */
    public void assignLockedRate(double lockedRate) {
        this.lockedRate = lockedRate;
    }

    public boolean isExpired(LocalDateTime now) {
        return status == ReservationStatus.RESERVED && expiresAt != null && expiresAt.isBefore(now);
    }

    public void cancel(boolean autoExpired) {
        if (status == ReservationStatus.CANCELLED) {
            throw new BusinessException(BusinessErrorCode.ALREADY_CANCELLED);
        }
        if (status == ReservationStatus.COMPLETED) {
            throw new BusinessException(BusinessErrorCode.ALREADY_COMPLETED);
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
