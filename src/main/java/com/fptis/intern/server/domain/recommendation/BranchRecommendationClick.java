package com.fptis.intern.server.domain.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "branch_recommendation_clicks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchRecommendationClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation_item_id", nullable = false)
    private Long recommendationItemId;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    public static BranchRecommendationClick of(Long recommendationItemId) {
        BranchRecommendationClick click = new BranchRecommendationClick();
        click.recommendationItemId = recommendationItemId;
        click.clickedAt = LocalDateTime.now();
        return click;
    }
}
