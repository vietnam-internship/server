package com.fptis.intern.server.presentation.branch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BranchRateBulkUpdateRequest(@NotEmpty List<@Valid BranchRateAdminItem> rates) {
}
