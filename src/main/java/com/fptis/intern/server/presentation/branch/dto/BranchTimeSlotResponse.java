package com.fptis.intern.server.presentation.branch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.List;

public record BranchTimeSlotResponse(
        @Schema(description = "지점이 그 날짜에 영업하는지 여부") boolean open,
        @Schema(description = "30분 단위 슬롯 목록 (영업 안 하면 빈 배열)") List<Slot> slots
) {
    public record Slot(
            @Schema(description = "슬롯 시작 시각 (예: 09:00)") LocalTime time,
            @Schema(description = "남은 예약 가능 정원") int remaining
    ) {
    }
}
