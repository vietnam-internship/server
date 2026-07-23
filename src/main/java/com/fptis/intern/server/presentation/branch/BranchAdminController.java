package com.fptis.intern.server.presentation.branch;

import com.fptis.intern.server.application.branch.BranchAdminService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.exception.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/branches")
@RequiredArgsConstructor
public class BranchAdminController {

    private final BranchAdminService branchAdminService;

    // TODO: Auth 연동 완료 후 SecurityContext(JwtToken 등)에서 실제 접속 직원의 소속 지점 ID를 추출하도록 변경
    private Long extractTokenBranchId() {
        return 1L; // 임시 하드코딩
    }

    @RequireAuth(roles = {"BRANCH_ADMIN", "ADMIN"})
    @GetMapping("/{id}/reservations")
    public ApiResponse<?> getReservations(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Long tokenBranchId = extractTokenBranchId();
        return ApiResponse.success(branchAdminService.getReservations(id, tokenBranchId, date));
    }
}
