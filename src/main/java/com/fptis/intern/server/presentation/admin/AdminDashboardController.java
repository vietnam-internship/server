package com.fptis.intern.server.presentation.admin;

import com.fptis.intern.server.application.admin.AdminDashboardService;
import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.annotation.UserId;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard", description = "관리자 대시보드 API (ADMIN / BRANCH_ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final UserRepository userRepository;

    @Operation(summary = "관리자 대시보드 조회", description = "총 유저 수, 대기 예약 수, 인기 통화, 최근 예약을 조회합니다.")
    @RequireAuth(roles = {"ADMIN", "BRANCH_ADMIN"})
    @GetMapping
    public ApiResponse<?> getDashboard(@UserId Long userId, @RequestParam(required = false) Long branchId) {
        return ApiResponse.success(adminDashboardService.getDashboard(resolveBranchId(userId, branchId)));
    }

    private Long resolveBranchId(Long userId, Long requestedBranchId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));
        return user.getRole() == Role.BRANCH_ADMIN ? user.getBranchId() : requestedBranchId;
    }
}
