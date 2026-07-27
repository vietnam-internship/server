package com.fptis.intern.server.domain.recommendation;

import com.fptis.intern.server.global.base.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 환전소 추천 세션. Client 요청 시 PENDING으로 생성되고, AI 서버가 결과를 푸시하면 COMPLETED로 전환된다.
 * AI 호출 실패 시 FAILED로 전환해 폴링 클라이언트가 오류를 확인할 수 있도록 한다.
 */
@Getter
@Entity
@Table(name = "branch_recommendations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchRecommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "radius_km", nullable = false)
    private double radiusKm;

    @Builder
    private BranchRecommendation(Long userId, String currency, BigDecimal amount,
                                  BigDecimal latitude, BigDecimal longitude, double radiusKm) {
        this.userId = userId;
        this.currency = currency;
        this.amount = amount;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = radiusKm;
        this.status = RecommendationStatus.PENDING;
    }

    public void complete() {
        this.status = RecommendationStatus.COMPLETED;
    }

    public void fail() {
        this.status = RecommendationStatus.FAILED;
    }
}
