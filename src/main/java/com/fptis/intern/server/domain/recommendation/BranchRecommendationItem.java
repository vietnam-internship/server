package com.fptis.intern.server.domain.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI가 푸시한 환전소 추천 랭킹 아이템. updated_at 컬럼이 없어 BaseTimeEntity를 사용하지 않는다.
 * score는 AI가 산출한 종합 점수이며, *Score 필드는 AI가 전달한 요소별 점수 breakdown이다.
 */
@Getter
@Entity
@Table(name = "branch_recommendation_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchRecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private int ranking;

    @Column(precision = 6, scale = 4)
    private BigDecimal score;

    @Column(name = "distance_score", precision = 6, scale = 4)
    private BigDecimal distanceScore;

    @Column(name = "rate_score", precision = 6, scale = 4)
    private BigDecimal rateScore;

    @Column(name = "availability_score", precision = 6, scale = 4)
    private BigDecimal availabilityScore;

    @Column(name = "reservation_score", precision = 6, scale = 4)
    private BigDecimal reservationScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    private BranchRecommendationItem(Long recommendationId, Long branchId, int ranking, BigDecimal score,
                                      BigDecimal distanceScore, BigDecimal rateScore,
                                      BigDecimal availabilityScore, BigDecimal reservationScore) {
        this.recommendationId = recommendationId;
        this.branchId = branchId;
        this.ranking = ranking;
        this.score = score;
        this.distanceScore = distanceScore;
        this.rateScore = rateScore;
        this.availabilityScore = availabilityScore;
        this.reservationScore = reservationScore;
    }
}
