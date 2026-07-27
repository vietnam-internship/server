package com.fptis.intern.server.presentation.recommendation;

import com.fptis.intern.server.application.recommendation.BranchRecommendationService;
import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.recommendation.dto.BranchRecommendationCreateRequest;
import com.fptis.intern.server.presentation.recommendation.dto.BranchRecommendationCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Branch Recommendation", description = "AI 환전소 추천 API (On-Demand 비동기)")
@RestController
@RequestMapping("/branches/recommendations")
@RequiredArgsConstructor
public class BranchRecommendationController {

    private final BranchRecommendationService branchRecommendationService;

    @Operation(
            summary = "AI 환전소 추천 요청",
            description = """
                    위치·통화·금액 정보를 AI에 전달해 주변 환전소 랭킹 연산을 시작합니다.
                    연산은 비동기로 진행되므로 202 Accepted와 함께 sessionId를 즉시 반환합니다.
                    결과는 GET /branches/recommendations?sessionId={id}로 폴링해 확인하세요.
                    """
    )
    @SecurityRequirements
    @PublicApi
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping
    public ApiResponse<?> createRecommendation(@Valid @RequestBody BranchRecommendationCreateRequest request) {
        Long sessionId = branchRecommendationService.createRecommendation(request, null);
        return ApiResponse.success(new BranchRecommendationCreateResponse(sessionId));
    }

    @Operation(
            summary = "AI 환전소 추천 결과 조회 (폴링)",
            description = """
                    sessionId로 추천 세션의 상태와 결과를 조회합니다.
                    - status=PENDING: AI 연산 진행 중, results는 빈 배열
                    - status=COMPLETED: 추천 목록(ranking 오름차순) 반환
                    - status=FAILED: AI 서버 오류, results는 빈 배열
                    """
    )
    @SecurityRequirements
    @PublicApi
    @GetMapping
    public ApiResponse<?> getRecommendation(
            @Parameter(description = "추천 세션 ID (POST /branches/recommendations에서 발급)") @RequestParam Long sessionId) {
        return ApiResponse.success(branchRecommendationService.getRecommendation(sessionId));
    }

    @Operation(
            summary = "추천 환전소 클릭 이벤트 기록",
            description = "사용자가 추천 목록의 환전소를 클릭할 때 호출합니다. CTR 측정용 클릭 로그를 적재합니다."
    )
    @SecurityRequirements
    @PublicApi
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/items/{itemId}/click")
    public void recordClick(
            @Parameter(description = "클릭한 추천 아이템 ID") @PathVariable Long itemId) {
        branchRecommendationService.recordClick(itemId);
    }
}
