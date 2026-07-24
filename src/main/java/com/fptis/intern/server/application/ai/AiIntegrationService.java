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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        RecommendationSignal signal = RecommendationSignal.builder()
                .currencyId(currencyId)
                .signalType(request.signalType())
                .windowDays(request.windowDays())
                .value(request.value())
                .build();

        return AiCreateResponse.from(recommendationSignalRepository.save(signal).getId());
    }

    @Transactional
    public AiCreateResponse createMacroIndicator(MacroIndicatorCreateRequest request) {
        MacroIndicator indicator = MacroIndicator.builder()
                .countryCode(request.countryCode())
                .indicatorType(request.indicatorType())
                .value(request.value())
                .recordedAt(request.recordedAt())
                .build();

        return AiCreateResponse.from(macroIndicatorRepository.save(indicator).getId());
    }

    @Transactional
    public AiCreateResponse createBacktestResult(String currencyCode, BacktestCreateRequest request) {
        Long currencyId = resolveCurrencyId(currencyCode);

        BacktestResult result = BacktestResult.builder()
                .currencyId(currencyId)
                .strategyType(request.strategyType())
                .periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .totalSignals(request.totalSignals())
                .correctSignals(request.correctSignals())
                .accuracyRate(request.accuracyRate())
                .build();

        return AiCreateResponse.from(backtestResultRepository.save(result).getId());
    }

    @Transactional
    public AiCreateResponse createAiRecommendation(String currencyCode, AiRecommendationCreateRequest request) {
        Long currencyId = resolveCurrencyId(currencyCode);

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

        return AiCreateResponse.from(savedRecommendation.getId());
    }
}
