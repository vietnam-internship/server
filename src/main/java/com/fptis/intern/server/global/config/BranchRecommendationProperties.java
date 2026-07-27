package com.fptis.intern.server.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GET /branches/recommend의 total_score 가중치(PRD §18.2). 서비스 정책 값이라 하드코딩하지
 * 않고 설정으로 분리한다 — 합이 반드시 1.0일 필요는 없지만(정규화된 점수 0~1 가중합), 기본값은
 * PRD가 제시한 0.35/0.35/0.20/0.10을 그대로 쓴다.
 */
@ConfigurationProperties(prefix = "travelx.branch.recommend")
public record BranchRecommendationProperties(double distanceWeight, double rateWeight,
                                              double availabilityWeight, double reservationWeight) {
}
