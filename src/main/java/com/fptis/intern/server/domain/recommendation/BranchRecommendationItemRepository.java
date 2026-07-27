package com.fptis.intern.server.domain.recommendation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRecommendationItemRepository extends JpaRepository<BranchRecommendationItem, Long> {

    List<BranchRecommendationItem> findByRecommendationIdOrderByRankingAsc(Long recommendationId);
}
