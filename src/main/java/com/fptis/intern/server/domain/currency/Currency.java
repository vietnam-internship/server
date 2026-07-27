package com.fptis.intern.server.domain.currency;

import com.fptis.intern.server.global.base.BaseTimeEntity;
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

@Getter
@Entity
@Table(name = "currencies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Currency extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(name = "buy_rate", nullable = false)
    private double buyRate;

    @Column(name = "sell_rate", nullable = false)
    private double sellRate;

    @Builder
    private Currency(String code, String country, double buyRate, double sellRate) {
        this.code = code;
        this.country = country;
        this.buyRate = buyRate;
        this.sellRate = sellRate;
    }

    public void updateRates(double buyRate, double sellRate) {
        this.buyRate = buyRate;
        this.sellRate = sellRate;
    }

    /**
     * preferentialRate는 백분율 숫자다(예: 0.5 = 0.5%) — 지점이 매도 환율에서 이 비율만큼 할인해 판매한다.
     */
    public double calculateFinalRate(double preferentialRate) {
        return sellRate * (1 - preferentialRate / 100);
    }
}
