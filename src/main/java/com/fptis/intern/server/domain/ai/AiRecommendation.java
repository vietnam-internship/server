package com.fptis.intern.server.domain.ai;

import jakarta.persistence.*;
import com.fptis.intern.server.global.base.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_recommendations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRecommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_id", nullable = false)
    private Long currencyId;

    @Column(name = "recommendation", nullable = false)
    @Enumerated(EnumType.STRING)
    private RecommendationType recommendation;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public enum RecommendationType {
        INCREASING, NEUTRAL, DECREASING
    }

    @Builder
    private AiRecommendation(Long currencyId, RecommendationType recommendation, String rationale, Double confidenceScore, String modelVersion, LocalDateTime expiresAt) {
        this.currencyId = currencyId;
        this.recommendation = recommendation;
        this.rationale = rationale;
        this.confidenceScore = confidenceScore;
        this.modelVersion = modelVersion;
        this.expiresAt = expiresAt;
    }
}
