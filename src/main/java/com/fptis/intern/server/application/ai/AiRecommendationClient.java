package com.fptis.intern.server.application.ai;

import com.fptis.intern.server.domain.recommendation.BranchRecommendationRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiRecommendationClient {

    private static final String BRANCH_RANKING_PATH = "/recommend/branches";

    private final WebClient aiWebClient;
    private final BranchRecommendationRepository recommendationRepository;

    /**
     * AI 서버에 환전소 랭킹 연산 시작을 비동기로 요청한다 (fire-and-forget).
     * 요청 실패 시 세션 상태를 FAILED로 전환한다 — 예외를 호출자로 전파하지 않는다.
     */
    public void triggerBranchRanking(Long sessionId, BigDecimal latitude, BigDecimal longitude,
                                      double radiusKm, String currencyCode, BigDecimal amount) {
        AiBranchRankingRequest request = new AiBranchRankingRequest(
                sessionId, latitude, longitude, radiusKm, currencyCode, amount
        );

        aiWebClient.post()
                .uri(BRANCH_RANKING_PATH)
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        response -> log.debug("AI 추천 연산 요청 전송 완료, sessionId={}", sessionId),
                        error -> {
                            log.warn("AI 추천 연산 요청 실패, sessionId={}: {}", sessionId, error.getMessage());
                            recommendationRepository.findById(sessionId).ifPresent(session -> {
                                session.fail();
                                recommendationRepository.save(session);
                            });
                        }
                );
    }

    record AiBranchRankingRequest(
            Long sessionId,
            BigDecimal latitude,
            BigDecimal longitude,
            double radiusKm,
            String currencyCode,
            BigDecimal amount
    ) {}
}
