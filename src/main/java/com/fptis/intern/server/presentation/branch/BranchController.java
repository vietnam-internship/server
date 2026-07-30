package com.fptis.intern.server.presentation.branch;

import com.fptis.intern.server.application.branch.BranchService;
import com.fptis.intern.server.domain.branch.BranchSortType;
import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.exception.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Branch", description = "지점 조회 API")
@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @Operation(summary = "지점 목록 조회", description = "환전 가능 통화, 위경도, 정렬 기준으로 지점 목록을 조회합니다. 인증이 필요 없는 공개 API입니다.")
    @PublicApi
    @SecurityRequirements
    @GetMapping
    public ApiResponse<?> listBranches(
            @Parameter(description = "조회할 통화 코드 (예: USD)") @RequestParam(required = false) String currency,
            @Parameter(description = "사용자 현재 위치 위도 (거리순 정렬 시 사용)") @RequestParam(required = false) Double latitude,
            @Parameter(description = "사용자 현재 위치 경도 (거리순 정렬 시 사용)") @RequestParam(required = false) Double longitude,
            @Parameter(description = "정렬 기준") @RequestParam(defaultValue = "RATE") BranchSortType sort) {
        return ApiResponse.success(branchService.listBranches(currency, latitude, longitude, sort));
    }

    @Operation(summary = "지점 상세 조회", description = "지점 ID로 지점 상세 정보와 통화별 환율을 조회합니다. 인증이 필요 없는 공개 API입니다.")
    @PublicApi
    @SecurityRequirements
    @GetMapping("/{id}")
    public ApiResponse<?> getBranch(@Parameter(description = "지점 ID") @PathVariable Long id) {
        return ApiResponse.success(branchService.getBranch(id));
    }

    @Operation(summary = "지점 예약 가능 슬롯 조회",
            description = "지점의 특정 날짜 30분 단위 슬롯과 잔여 정원을 조회합니다. 인증이 필요 없는 공개 API입니다.")
    @PublicApi
    @SecurityRequirements
    @GetMapping("/{id}/time-slots")
    public ApiResponse<?> getTimeSlots(@Parameter(description = "지점 ID") @PathVariable Long id,
                                        @Parameter(description = "조회할 날짜 (예: 2026-08-01)")
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(branchService.getTimeSlots(id, date));
    }

}
