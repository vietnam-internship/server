package com.fptis.intern.server.domain.currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "exchange_rate_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private double rate;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

    @Builder
    private ExchangeRateHistory(Currency currency, double rate, LocalDate recordedAt) {
        this.currency = currency;
        this.rate = rate;
        this.recordedAt = recordedAt;
    }
}
