package com.fptis.intern.server.presentation.ai;

import com.fptis.intern.server.application.ai.AiIntegrationService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.ai.dto.AiCreateResponse;
import com.fptis.intern.server.presentation.ai.dto.AiRecommendationCreateRequest;
import com.fptis.intern.server.presentation.ai.dto.AiSignalCreateRequest;
import com.fptis.intern.server.presentation.ai.dto.BacktestCreateRequest;
import com.fptis.intern.server.presentation.ai.dto.MacroIndicatorCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class AiInternalController {

    private final AiIntegrationService aiIntegrationService;

    @RequireAuth(roles = "AI_AGENT")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/currencies/{code}/signals")
    public ApiResponse<?> createAiSignal(
            @PathVariable String code,
            @Valid @RequestBody AiSignalCreateRequest request) {
        return ApiResponse.success(aiIntegrationService.createAiSignal(code, request));
    }

    @RequireAuth(roles = "AI_AGENT")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/macro-indicators")
    public ApiResponse<?> createMacroIndicator(
            @Valid @RequestBody MacroIndicatorCreateRequest request) {
        return ApiResponse.success(aiIntegrationService.createMacroIndicator(request));
    }

    @RequireAuth(roles = "AI_AGENT")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/currencies/{code}/backtests")
    public ApiResponse<?> createBacktestResult(
            @PathVariable String code,
            @Valid @RequestBody BacktestCreateRequest request) {
        return ApiResponse.success(aiIntegrationService.createBacktestResult(code, request));
    }

    @RequireAuth(roles = "AI_AGENT")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/currencies/{code}/recommendations")
    public ApiResponse<?> createAiRecommendation(
            @PathVariable String code,
            @Valid @RequestBody AiRecommendationCreateRequest request) {
        return ApiResponse.success(aiIntegrationService.createAiRecommendation(code, request));
    }
}
