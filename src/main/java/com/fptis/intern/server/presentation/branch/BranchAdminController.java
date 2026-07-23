package com.fptis.intern.server.presentation.branch;

import com.fptis.intern.server.application.branch.BranchService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchCreateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchRateUpdateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/branches")
@RequiredArgsConstructor
public class BranchAdminController {

    private final BranchService branchService;

    // TODO(#40): UserRole이 USER/BRANCH_ADMIN/ADMIN/AI_AGENT 4종으로 분리되면
    // 지점 생성/수정은 시스템 관리자 영역이므로 ADMIN을 유지한다.
    @RequireAuth(roles = "ADMIN")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<?> createBranch(@Valid @RequestBody BranchCreateRequest request) {
        return ApiResponse.success(branchService.createBranch(request));
    }

    @RequireAuth(roles = "ADMIN")
    @PatchMapping("/{id}")
    public ApiResponse<?> updateBranch(@PathVariable Long id, @RequestBody BranchUpdateRequest request) {
        return ApiResponse.success(branchService.updateBranch(id, request));
    }

    // TODO(#40): UserRole에 BRANCH_ADMIN이 추가되면 roles를 "BRANCH_ADMIN"으로 변경한다 —
    // 지점 직원이 매일 아침 자기 지점의 우대율/재고를 설정하는 API이지 시스템 관리자용이 아니다.
    // #40이 develop에 아직 머지되지 않아 BRANCH_ADMIN 역할을 가진 유저가 없으므로 우선 ADMIN으로 둔다.
    @RequireAuth(roles = "ADMIN")
    @PatchMapping("/{id}/rate")
    public ApiResponse<?> updateBranchRate(@PathVariable Long id, @Valid @RequestBody BranchRateUpdateRequest request) {
        return ApiResponse.success(branchService.updateBranchRate(id, request));
    }
}
