package com.fptis.intern.server.presentation.ai;

import com.fptis.intern.server.application.ai.AiIntegrationService;
import com.fptis.intern.server.application.recommendation.BranchRecommendationService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.ai.dto.AiCreateResponse;
import com.fptis.intern.server.presentation.ai.dto.AiRecommendationCreateRequest;
import com.fptis.intern.server.presentation.ai.dto.AiSignalCreateRequest;
import com.fptis.intern.server.presentation.ai.dto.BacktestCreateRequest;
import com.fptis.intern.server.presentation.ai.dto.BranchRecommendationPushRequest;
import com.fptis.intern.server.presentation.ai.dto.MacroIndicatorCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Internal", description = "AI 모듈 전용 내부 API (AI_AGENT 권한 필요, 외부 접근 불가)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class AiInternalController {

    private final AiIntegrationService aiIntegrationService;
    private final BranchRecommendationService branchRecommendationService;

    @Operation(summary = "(Internal) AI 통계 시그널 적재", description = "AI 분석에 사용된 이동평균선 등 보조 지표 데이터를 저장합니다.")
    @RequireAuth(roles = "AI_AGENT")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/currencies/{code}/signals")
    public ApiResponse<?> createAiSignal(
            @Parameter(description = "ISO 4217 통화 코드") @PathVariable String code,
            @Valid @RequestBody AiSignalCreateRequest request) {
        return ApiResponse.success(aiIntegrationService.createAiSignal(code, request));
    }

    @Operation(summary = "(Internal) 거시경제 지표 적재", description = "AI 모듈이 주기적으로 수집한 국가별 선행 지표(금리, 인플레이션 등)를 저장합니다.")
    @RequireAuth(roles = "AI_AGENT")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/macro-indicators")
    public ApiResponse<?> createMacroIndicator(
            @Valid @RequestBody MacroIndicatorCreateRequest request) {
        return ApiResponse.success(aiIntegrationService.createMacroIndicator(request));
    }

    @Operation(summary = "(Internal) 백테스트 결과 저장", description = "주기적으로 실행된 AI 모델 적중률 검증 결과를 저장합니다.")
    @RequireAuth(roles = "AI_AGENT")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/currencies/{code}/backtests")
    public ApiResponse<?> createBacktestResult(
            @Parameter(description = "ISO 4217 통화 코드") @PathVariable String code,
            @Valid @RequestBody BacktestCreateRequest request) {
        return ApiResponse.success(aiIntegrationService.createBacktestResult(code, request));
    }

    @Operation(summary = "(Internal) AI 환율 추천 결과 저장", description = "AI 모듈이 계산한 최신 환율 추천 등급 및 근거를 저장합니다.")
    @RequireAuth(roles = "AI_AGENT")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/currencies/{code}/recommendations")
    public ApiResponse<?> createAiRecommendation(
            @Parameter(description = "ISO 4217 통화 코드") @PathVariable String code,
            @Valid @RequestBody AiRecommendationCreateRequest request) {
        return ApiResponse.success(aiIntegrationService.createAiRecommendation(code, request));
    }

    @Operation(
            summary = "(Internal) AI 환전소 추천 결과 푸시",
            description = """
                    AI 인스턴스가 환전소 랭킹 연산을 완료한 뒤 결과를 밀어 넣는 콜백 API입니다.
                    sessionId로 해당 세션을 찾아 ranked items를 저장하고 상태를 COMPLETED로 전환합니다.
                    """
    )
    @RequireAuth(roles = "AI_AGENT")
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/recommendations/branches")
    public ApiResponse<?> pushBranchRecommendation(@Valid @RequestBody BranchRecommendationPushRequest request) {
        branchRecommendationService.receiveAiResult(request);
        return ApiResponse.success(null);
    }
}
