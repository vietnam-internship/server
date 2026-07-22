package com.fptis.intern.server.domain.branch;

import com.fptis.intern.server.global.base.BaseTimeEntity;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지점이 통화별로 설정하는 우대율/예약 전용 재고. 기준 환율(buyRate/sellRate)은
 * Currency 도메인이 아직 없어 이 엔티티에 포함하지 않는다 — finalRate 계산은 #21에서 연동한다.
 */
@Getter
@Entity
@Table(name = "branch_currency_rates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchCurrencyRate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "preferential_rate", nullable = false)
    private double preferentialRate;

    @Column(name = "reservation_only_stock", nullable = false)
    private double reservationOnlyStock;

    @Builder
    private BranchCurrencyRate(Long branchId, String currencyCode, double preferentialRate, double reservationOnlyStock) {
        this.branchId = branchId;
        this.currencyCode = currencyCode;
        this.preferentialRate = preferentialRate;
        this.reservationOnlyStock = reservationOnlyStock;
    }

    public void update(Double preferentialRate, Double reservationOnlyStock) {
        if (preferentialRate != null) {
            this.preferentialRate = preferentialRate;
        }
        if (reservationOnlyStock != null) {
            this.reservationOnlyStock = reservationOnlyStock;
        }
    }

    public boolean hasStock() {
        return reservationOnlyStock > 0;
    }

    /**
     * 예약 생성 시 호출한다 — 반드시 비관적 락으로 조회한 행에서 호출해 동시 예약 초과 판매를 막는다.
     */
    public void decreaseStock(double amount) {
        if (reservationOnlyStock < amount) {
            throw new BusinessException(BusinessErrorCode.STOCK_EXCEEDED);
        }
        this.reservationOnlyStock -= amount;
    }

    public void increaseStock(double amount) {
        this.reservationOnlyStock += amount;
    }
}
