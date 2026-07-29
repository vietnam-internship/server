package com.fptis.intern.server.application.ai;

import com.fptis.intern.server.domain.ai.AiRecommendation;
import com.fptis.intern.server.domain.ai.AiRecommendationRepository;
import com.fptis.intern.server.domain.ai.AiRecommendationSignal;
import com.fptis.intern.server.domain.ai.AiRecommendationSignalRepository;
import com.fptis.intern.server.domain.ai.BacktestResult;
import com.fptis.intern.server.domain.ai.BacktestResultRepository;
import com.fptis.intern.server.domain.ai.MacroIndicator;
import com.fptis.intern.server.domain.ai.MacroIndicatorRepository;
import com.fptis.intern.server.domain.ai.RecommendationSignal;
import com.fptis.intern.server.domain.ai.RecommendationSignalRepository;
import com.fptis.intern.server.presentation.ai.dto.AiCreateResponse;
import com.fptis.intern.server.presentation.ai.dto.AiRecommendationCreateRequest;
import com.fptis.intern.server.presentation.ai.dto.AiSignalCreateRequest;
import com.fptis.intern.server.presentation.ai.dto.BacktestCreateRequest;
import com.fptis.intern.server.presentation.ai.dto.MacroIndicatorCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiIntegrationService {

    private final RecommendationSignalRepository recommendationSignalRepository;
    private final MacroIndicatorRepository macroIndicatorRepository;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final AiRecommendationSignalRepository aiRecommendationSignalRepository;
    private final BacktestResultRepository backtestResultRepository;

    // TODO: Currency 도메인 구현 후 실제 DB 조회 로직으로 변경
    private Long resolveCurrencyId(String currencyCode) {
        return 1L; // 임시 하드코딩
    }

    @Transactional
    public AiCreateResponse createAiSignal(String currencyCode, AiSignalCreateRequest request) {
        Long currencyId = resolveCurrencyId(currencyCode);
        log.info("[AI 신호 저장] currencyCode={}, signalType={}, windowDays={}, value={}",
                currencyCode, request.signalType(), request.windowDays(), request.value());

        RecommendationSignal signal = RecommendationSignal.builder()
                .currencyId(currencyId)
                .signalType(request.signalType())
                .windowDays(request.windowDays())
                .value(request.value())
                .build();

        Long savedId = recommendationSignalRepository.save(signal).getId();
        log.info("[AI 신호 저장 완료] signalId={}", savedId);
        return AiCreateResponse.from(savedId);
    }

    @Transactional
    public AiCreateResponse createMacroIndicator(MacroIndicatorCreateRequest request) {
        log.info("[매크로 지표 저장] countryCode={}, indicatorType={}, value={}, recordedAt={}",
                request.countryCode(), request.indicatorType(), request.value(), request.recordedAt());

        MacroIndicator indicator = MacroIndicator.builder()
                .countryCode(request.countryCode())
                .indicatorType(request.indicatorType())
                .value(request.value())
                .recordedAt(request.recordedAt())
                .build();

        Long savedId = macroIndicatorRepository.save(indicator).getId();
        log.info("[매크로 지표 저장 완료] indicatorId={}", savedId);
        return AiCreateResponse.from(savedId);
    }

    @Transactional
    public AiCreateResponse createBacktestResult(String currencyCode, BacktestCreateRequest request) {
        Long currencyId = resolveCurrencyId(currencyCode);
        log.info("[백테스트 결과 저장] currencyCode={}, strategyType={}, period={}/{}",
                currencyCode, request.strategyType(), request.periodStart(), request.periodEnd());

        BacktestResult result = BacktestResult.builder()
                .currencyId(currencyId)
                .strategyType(request.strategyType())
                .periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .totalSignals(request.totalSignals())
                .correctSignals(request.correctSignals())
                .accuracyRate(request.accuracyRate())
                .build();

        Long savedId = backtestResultRepository.save(result).getId();
        log.info("[백테스트 결과 저장 완료] backtestId={}, accuracyRate={}", savedId, request.accuracyRate());
        return AiCreateResponse.from(savedId);
    }

    @Transactional
    public AiCreateResponse createAiRecommendation(String currencyCode, AiRecommendationCreateRequest request) {
        Long currencyId = resolveCurrencyId(currencyCode);
        log.info("[AI 추천 저장] currencyCode={}, recommendation={}, confidenceScore={}, signalIds={}",
                currencyCode, request.recommendation(), request.confidenceScore(), request.signalIds());

        // 1. 추천 결과 본체 저장
        AiRecommendation recommendation = AiRecommendation.builder()
                .currencyId(currencyId)
                .recommendation(request.recommendation())
                .rationale(request.rationale())
                .confidenceScore(request.confidenceScore())
                .modelVersion(request.modelVersion())
                .expiresAt(null) // 현재 API 스펙에 없으므로 일단 null 처리, 필요시 정책 추가
                .build();
        
        AiRecommendation savedRecommendation = aiRecommendationRepository.save(recommendation);

        // 2. M:N 시그널 매핑 저장 (Cascade 역할을 수동으로 원자적 처리)
        List<AiRecommendationSignal> mappings = request.signalIds().stream()
                .map(signalId -> AiRecommendationSignal.builder()
                        .recommendationId(savedRecommendation.getId())
                        .signalId(signalId)
                        .build())
                .toList();
        
        aiRecommendationSignalRepository.saveAll(mappings);
        log.info("[AI 추천 저장 완료] recommendationId={}, 매핑된 신호 수={}", savedRecommendation.getId(), mappings.size());

        return AiCreateResponse.from(savedRecommendation.getId());
    }
}
