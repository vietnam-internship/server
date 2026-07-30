package com.fptis.intern.server.presentation.branch;

import com.fptis.intern.server.application.branch.BranchAdminService;
import com.fptis.intern.server.application.branch.BranchService;
import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.annotation.UserId;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.branch.dto.BranchCreateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchInventoryBulkUpdateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchRateBulkUpdateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchRateUpdateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Branch Admin", description = "지점 관리자용 API (ADMIN / BRANCH_ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/branches")
@RequiredArgsConstructor
public class BranchAdminController {

    private final BranchService branchService;
    private final BranchAdminService branchAdminService;
    private final UserRepository userRepository;

    @Operation(summary = "지점 등록", description = "새 지점을 등록합니다. ADMIN 권한이 필요합니다.")
    @RequireAuth(roles = "ADMIN")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<?> createBranch(@Valid @RequestBody BranchCreateRequest request) {
        return ApiResponse.success(branchService.createBranch(request));
    }

    // 지점 직원(BRANCH_ADMIN)이 /admin/settings에서 자기 지점 정보를 직접 고칠 수 있도록
    // rate/inventory 엔드포인트와 같은 패턴(assertBranchAccess)으로 권한 확장.
    @Operation(summary = "지점 정보 수정", description = "지점 정보를 부분 수정합니다. null이 아닌 필드만 반영됩니다. BRANCH_ADMIN은 자기 지점만, ADMIN은 모든 지점을 수정할 수 있습니다.")
    @RequireAuth(roles = {"BRANCH_ADMIN", "ADMIN"})
    @PatchMapping("/{id}")
    public ApiResponse<?> updateBranch(@UserId Long userId, @Parameter(description = "지점 ID") @PathVariable Long id, @RequestBody BranchUpdateRequest request) {
        assertBranchAccess(userId, id);
        return ApiResponse.success(branchService.updateBranch(id, request));
    }

    // PR #40 반영: 지점 직원이 우대율을 설정할 수 있도록 BRANCH_ADMIN 권한 추가
    @Operation(summary = "지점 통화별 우대율 수정", description = "지점의 특정 통화에 대한 우대율/예약 전용 재고를 수정합니다. BRANCH_ADMIN 또는 ADMIN 권한이 필요합니다.")
    @RequireAuth(roles = {"BRANCH_ADMIN", "ADMIN"})
    @PatchMapping("/{id}/rate")
    public ApiResponse<?> updateBranchRate(@Parameter(description = "지점 ID") @PathVariable Long id, @Valid @RequestBody BranchRateUpdateRequest request) {
        return ApiResponse.success(branchService.updateBranchRate(id, request));
    }

    @Operation(summary = "지점 환율 조회", description = "지점의 통화별 Buy/Sell(전역)/Fee(지점별)를 조회합니다.")
    @RequireAuth(roles = {"ADMIN", "BRANCH_ADMIN"})
    @GetMapping("/{id}/rates")
    public ApiResponse<?> getRates(@UserId Long userId, @PathVariable Long id) {
        assertBranchAccess(userId, id);
        return ApiResponse.success(branchAdminService.getRates(id));
    }

    @Operation(summary = "지점 환율 저장", description = "지점의 통화별 Fee(우대율)를 일괄 저장합니다. Buy/Sell은 이 API로 변경할 수 없습니다.")
    @RequireAuth(roles = {"ADMIN", "BRANCH_ADMIN"})
    @PatchMapping("/{id}/rates")
    public ApiResponse<?> updateRates(@UserId Long userId, @PathVariable Long id,
                                       @Valid @RequestBody BranchRateBulkUpdateRequest request) {
        assertBranchAccess(userId, id);
        return ApiResponse.success(branchAdminService.updateRates(id, request));
    }

    @Operation(summary = "지점 재고 조회", description = "지점의 통화별 재고를 조회합니다.")
    @RequireAuth(roles = {"ADMIN", "BRANCH_ADMIN"})
    @GetMapping("/{id}/inventory")
    public ApiResponse<?> getInventory(@UserId Long userId, @PathVariable Long id) {
        assertBranchAccess(userId, id);
        return ApiResponse.success(branchAdminService.getInventory(id));
    }

    @Operation(summary = "지점 재고 조정", description = "지점의 통화별 재고를 일괄 조정합니다.")
    @RequireAuth(roles = {"ADMIN", "BRANCH_ADMIN"})
    @PatchMapping("/{id}/inventory")
    public ApiResponse<?> updateInventory(@UserId Long userId, @PathVariable Long id,
                                           @Valid @RequestBody BranchInventoryBulkUpdateRequest request) {
        assertBranchAccess(userId, id);
        return ApiResponse.success(branchAdminService.updateInventory(id, request));
    }

    private void assertBranchAccess(Long userId, Long pathBranchId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));
        if (user.getRole() == Role.BRANCH_ADMIN && !pathBranchId.equals(user.getBranchId())) {
            throw new BusinessException(BusinessErrorCode.NOT_YOUR_BRANCH);
        }
    }
}
