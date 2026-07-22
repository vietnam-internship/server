package com.fptis.intern.server.presentation.branch;

import com.fptis.intern.server.application.branch.BranchService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchCreateRequest;
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
}
