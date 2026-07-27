package com.fptis.intern.server.application.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fptis.intern.server.application.ai.AiRecommendationClient;
import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.domain.recommendation.BranchRecommendation;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationClick;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationClickRepository;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationItem;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationItemRepository;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationRepository;
import com.fptis.intern.server.domain.recommendation.RecommendationStatus;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.ai.dto.BranchRecommendationPushRequest;
import com.fptis.intern.server.presentation.ai.dto.RankedBranchItem;
import com.fptis.intern.server.presentation.branch.dto.ScoreBreakdown;
import com.fptis.intern.server.presentation.recommendation.dto.BranchRecommendationCreateRequest;
import com.fptis.intern.server.presentation.recommendation.dto.BranchRecommendationQueryResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BranchRecommendationService의 핵심 로직을 검증한다.
 * AiRecommendationClient는 @MockBean으로 대체해 외부 AI 서버 호출을 차단한다.
 * 세션 생성 → AI 푸시 수신 → 결과 조회 → 클릭 로그의 전체 상태 전이를 순서대로 검증한다.
 */
@Testcontainers
@DataJpaTest
@EnableJpaAuditing
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BranchRecommendationService.class)
class BranchRecommendationServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @MockitoBean
    private AiRecommendationClient aiRecommendationClient;

    @Autowired private BranchRecommendationService service;
    @Autowired private BranchRecommendationRepository recommendationRepository;
    @Autowired private BranchRecommendationItemRepository itemRepository;
    @Autowired private BranchRecommendationClickRepository clickRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private BranchCurrencyRateRepository branchCurrencyRateRepository;
    @Autowired private CurrencyRepository currencyRepository;

    // ── createRecommendation ──────────────────────────────────────────────────

    @Test
    void createRecommendation_createsPendingSession() {
        BranchRecommendationCreateRequest request =
                new BranchRecommendationCreateRequest(37.5665, 126.9780, 5.0, "USD", 1000.0);

        Long sessionId = service.createRecommendation(request, null);

        BranchRecommendation session = recommendationRepository.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(RecommendationStatus.PENDING);
        assertThat(session.getCurrency()).isEqualTo("USD");
        verify(aiRecommendationClient).triggerBranchRanking(
                eq(sessionId), any(), any(), anyDouble(), eq("USD"), any());
    }

    // ── receiveAiResult ───────────────────────────────────────────────────────

    @Test
    void receiveAiResult_savesItemsAndCompletesSession() {
        Branch branch = saveBranch("명동 환전소", 37.5665, 126.9780);
        Long sessionId = saveSession("USD");

        service.receiveAiResult(new BranchRecommendationPushRequest(sessionId, List.of(
                new RankedBranchItem(branch.getId(), 1, 0.9,
                        new ScoreBreakdown(0.9, 0.8, 0.7, 0.6))
        )));

        BranchRecommendation session = recommendationRepository.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(RecommendationStatus.COMPLETED);

        List<BranchRecommendationItem> items =
                itemRepository.findByRecommendationIdOrderByRankingAsc(sessionId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getBranchId()).isEqualTo(branch.getId());
        assertThat(items.get(0).getRanking()).isEqualTo(1);
        assertThat(items.get(0).getDistanceScore()).isNotNull();
    }

    @Test
    void receiveAiResult_throwsWhenSessionNotFound() {
        BranchRecommendationPushRequest push = new BranchRecommendationPushRequest(
                999L, List.of(new RankedBranchItem(1L, 1, 0.9, null)));

        assertThatThrownBy(() -> service.receiveAiResult(push))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.RECOMMENDATION_NOT_FOUND);
    }

    // ── getRecommendation ─────────────────────────────────────────────────────

    @Test
    void getRecommendation_returnsPendingStatus() {
        Long sessionId = saveSession("USD");

        BranchRecommendationQueryResponse response = service.getRecommendation(sessionId);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.results()).isEmpty();
    }

    @Test
    void getRecommendation_returnsCompletedResultsInRankOrder() {
        Branch first  = saveBranch("1순위 지점", 37.5665, 126.9780);
        Branch second = saveBranch("2순위 지점", 37.5666, 126.9781);
        currencyRepository.save(Currency.builder()
                .code("USD").country("미국").buyRate(1370.0).sellRate(1400.0).build());
        saveRate(first,  "USD", 2.0);  // finalRate = 1400 * 0.98 = 1372.0
        saveRate(second, "USD", 0.5);  // finalRate = 1400 * 0.995 = 1393.0

        Long sessionId = saveSession("USD");
        service.receiveAiResult(new BranchRecommendationPushRequest(sessionId, List.of(
                new RankedBranchItem(first.getId(),  1, 0.9, new ScoreBreakdown(0.9, 0.8, 0.7, 0.6)),
                new RankedBranchItem(second.getId(), 2, 0.7, new ScoreBreakdown(0.6, 0.5, 0.4, 0.3))
        )));

        BranchRecommendationQueryResponse response = service.getRecommendation(sessionId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.disclaimer()).isNotBlank();
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).ranking()).isEqualTo(1);
        assertThat(response.results().get(0).id()).isEqualTo(first.getId());
        assertThat(response.results().get(0).isBestRateNearby()).isTrue();  // finalRate 1372 < 1393
        assertThat(response.results().get(1).ranking()).isEqualTo(2);
        assertThat(response.results().get(1).id()).isEqualTo(second.getId());
        assertThat(response.results().get(1).isBestRateNearby()).isFalse();
    }

    @Test
    void getRecommendation_throwsWhenSessionNotFound() {
        assertThatThrownBy(() -> service.getRecommendation(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.RECOMMENDATION_NOT_FOUND);
    }

    // ── recordClick ───────────────────────────────────────────────────────────

    @Test
    void recordClick_savesClickLog() {
        Branch branch = saveBranch("명동 환전소", 37.5665, 126.9780);
        Long sessionId = saveSession("USD");
        service.receiveAiResult(new BranchRecommendationPushRequest(sessionId,
                List.of(new RankedBranchItem(branch.getId(), 1, 0.9, null))));
        BranchRecommendationItem item =
                itemRepository.findByRecommendationIdOrderByRankingAsc(sessionId).get(0);

        service.recordClick(item.getId());

        List<BranchRecommendationClick> clicks = clickRepository.findAll();
        assertThat(clicks).hasSize(1);
        assertThat(clicks.get(0).getRecommendationItemId()).isEqualTo(item.getId());
    }

    @Test
    void recordClick_throwsWhenItemNotFound() {
        assertThatThrownBy(() -> service.recordClick(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.RECOMMENDATION_ITEM_NOT_FOUND);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** DB에 PENDING 세션을 직접 저장한다 (AI 클라이언트 호출 없이). */
    private Long saveSession(String currency) {
        return recommendationRepository.save(BranchRecommendation.builder()
                .userId(null)
                .currency(currency)
                .amount(BigDecimal.valueOf(1000))
                .latitude(BigDecimal.valueOf(37.5665))
                .longitude(BigDecimal.valueOf(126.9780))
                .radiusKm(5.0)
                .build()).getId();
    }

    private Branch saveBranch(String name, double latitude, double longitude) {
        return branchRepository.save(Branch.builder()
                .name(name)
                .address("서울 중구 명동길 1")
                .latitude(latitude)
                .longitude(longitude)
                .phone("02-123-4567")
                .businessHours("평일 09:00-18:00")
                .timeSlotCapacity(4)
                .build());
    }

    private void saveRate(Branch branch, String currencyCode, double preferentialRate) {
        branchCurrencyRateRepository.save(BranchCurrencyRate.builder()
                .branchId(branch.getId())
                .currencyCode(currencyCode)
                .preferentialRate(preferentialRate)
                .reservationOnlyStock(1000)
                .build());
    }
}
