package com.fptis.intern.server.domain.ai;

import jakarta.persistence.*;
import com.fptis.intern.server.global.base.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_recommendation_signals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRecommendationSignal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    @Column(name = "signal_id", nullable = false)
    private Long signalId;

    @Builder
    private AiRecommendationSignal(Long recommendationId, Long signalId) {
        this.recommendationId = recommendationId;
        this.signalId = signalId;
    }
}
