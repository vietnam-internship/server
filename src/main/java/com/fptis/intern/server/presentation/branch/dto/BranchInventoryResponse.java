package com.fptis.intern.server.presentation.branch.dto;

public record BranchInventoryResponse(
        String currencyCode,
        double stock,
        boolean lowStock
) {
}
