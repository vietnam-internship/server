package com.fptis.intern.server.domain.ai;

import jakarta.persistence.*;
import com.fptis.intern.server.global.base.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "macro_indicators")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MacroIndicator extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(name = "indicator_type", nullable = false)
    private String indicatorType;

    @Column(name = "value", nullable = false)
    private Double value;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

    @Builder
    private MacroIndicator(String countryCode, String indicatorType, Double value, LocalDate recordedAt) {
        this.countryCode = countryCode;
        this.indicatorType = indicatorType;
        this.value = value;
        this.recordedAt = recordedAt;
    }
}
