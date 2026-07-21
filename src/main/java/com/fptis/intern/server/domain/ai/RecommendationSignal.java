package com.fptis.intern.server.domain.ai;

import jakarta.persistence.*;
import com.fptis.intern.server.global.base.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_signals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationSignal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_id", nullable = false)
    private Long currencyId;

    @Column(name = "signal_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SignalType signalType;

    @Column(name = "window_days", nullable = false)
    private Integer windowDays;

    @Column(name = "value", nullable = false)
    private Double value;

    public enum SignalType {
        MA, RSI, MACD, STD
    }

    @Builder
    private RecommendationSignal(Long currencyId, SignalType signalType, Integer windowDays, Double value) {
        this.currencyId = currencyId;
        this.signalType = signalType;
        this.windowDays = windowDays;
        this.value = value;
    }
}
