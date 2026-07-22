package com.fptis.intern.server.application.branch;

import com.fptis.intern.server.domain.branch.BranchTimeSlotOverride;
import com.fptis.intern.server.domain.branch.BranchTimeSlotOverrideRepository;
import com.fptis.intern.server.presentation.branch.dto.BranchReservationListResponse;
import com.fptis.intern.server.presentation.branch.dto.TimeSlotOverrideRequest;
import com.fptis.intern.server.presentation.branch.dto.TimeSlotOverrideResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchAdminService {

    private final BranchTimeSlotOverrideRepository branchTimeSlotOverrideRepository;
    // private final ReservationRepository reservationRepository; // TODO: Reservation 도메인 작업 시 주석 해제

    @Transactional
    public TimeSlotOverrideResponse overrideTimeSlot(Long pathBranchId, Long tokenBranchId, TimeSlotOverrideRequest request) {
        verifyBranchAccess(pathBranchId, tokenBranchId);

        BranchTimeSlotOverride override = BranchTimeSlotOverride.builder()
                .branchId(pathBranchId)
                .targetDate(request.targetDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .capacityLimit(request.capacityLimit())
                .isBlocked(request.isBlocked())
                .build();

        BranchTimeSlotOverride saved = branchTimeSlotOverrideRepository.save(override);
        return TimeSlotOverrideResponse.from(saved.getId());
    }

    public BranchReservationListResponse getReservations(Long pathBranchId, Long tokenBranchId, LocalDate date) {
        verifyBranchAccess(pathBranchId, tokenBranchId);

        // TODO: Reservation 도메인 구현 후 실제 DB 조회 및 변환 로직으로 교체
        // List<Reservation> reservations = reservationRepository.find...
        // return BranchReservationListResponse.from(reservations.stream().map(...).toList());
        
        return BranchReservationListResponse.from(List.of());
    }

    /**
     * BOLA(Broken Object Level Authorization) 방어 로직.
     * 로그인한 사용자가 속한 지점(tokenBranchId)과 요청한 지점(pathBranchId)이 다르면 예외를 발생시킵니다.
     * 시스템 관리자(ADMIN)의 경우 tokenBranchId가 null이거나 0이 들어올 수 있으므로 패스 처리합니다.
     */
    private void verifyBranchAccess(Long pathBranchId, Long tokenBranchId) {
        if (tokenBranchId != null && tokenBranchId > 0 && !tokenBranchId.equals(pathBranchId)) {
            throw new IllegalArgumentException("본인이 소속된 지점의 데이터만 접근할 수 있습니다."); 
        }
    }
}
