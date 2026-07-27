package com.fptis.intern.server.presentation.branch;

import com.fptis.intern.server.application.branch.BranchService;
import com.fptis.intern.server.domain.branch.BranchSortType;
import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.exception.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PublicApi
    @GetMapping("/recommend")
    public ApiResponse<?> recommendBranches(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam String currency,
            @RequestParam(name = "radius_km", defaultValue = "5") double radiusKm,
            @RequestParam(name = "top_n", defaultValue = "10") int topN) {
        return ApiResponse.success(branchService.recommendBranches(latitude, longitude, currency, radiusKm, topN));
    }
}
