package com.fptis.intern.server.presentation.branch;

import com.fptis.intern.server.application.branch.BranchService;
import com.fptis.intern.server.domain.branch.BranchSortType;
import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchRateUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @PublicApi
    @GetMapping
    public ApiResponse<?> listBranches(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "RATE") BranchSortType sort) {
        return ApiResponse.success(branchService.listBranches(currency, latitude, longitude, sort));
    }

    @PublicApi
    @GetMapping("/{id}")
    public ApiResponse<?> getBranch(@PathVariable Long id) {
        return ApiResponse.success(branchService.getBranch(id));
    }

    @RequireAuth(roles = "ADMIN")
    @PatchMapping("/{id}/rate")
    public ApiResponse<?> updateBranchRate(@PathVariable Long id, @Valid @RequestBody BranchRateUpdateRequest request) {
        return ApiResponse.success(branchService.updateBranchRate(id, request));
    }
}
