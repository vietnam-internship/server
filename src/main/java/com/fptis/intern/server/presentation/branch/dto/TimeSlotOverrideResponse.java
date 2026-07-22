package com.fptis.intern.server.presentation.branch.dto;

public record TimeSlotOverrideResponse(
        Long id
) {
    public static TimeSlotOverrideResponse from(Long id) {
        return new TimeSlotOverrideResponse(id);
    }
}
