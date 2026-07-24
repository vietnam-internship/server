package com.fptis.intern.server.domain.ai;

import jakarta.persistence.*;
import com.fptis.intern.server.global.base.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "backtest_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BacktestResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_id", nullable = false)
    private Long currencyId;

    @Column(name = "strategy_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private StrategyType strategyType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_signals", nullable = false)
    private Integer totalSignals;

    @Column(name = "correct_signals", nullable = false)
    private Integer correctSignals;

    @Column(name = "accuracy_rate", nullable = false)
    private Double accuracyRate;

    public enum StrategyType {
        LINEAR_REGRESSION, BASE_LINE, LOGISTIC_REGRESSION
    }

    @Builder
    private BacktestResult(Long currencyId, StrategyType strategyType, LocalDate periodStart, LocalDate periodEnd, Integer totalSignals, Integer correctSignals, Double accuracyRate) {
        this.currencyId = currencyId;
        this.strategyType = strategyType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalSignals = totalSignals;
        this.correctSignals = correctSignals;
        this.accuracyRate = accuracyRate;
    }
}
