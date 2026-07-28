package com.fptis.intern.server.presentation.admin;

import com.fptis.intern.server.application.admin.AdminReservationService;
import com.fptis.intern.server.application.reservation.ReservationService;
import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.annotation.UserId;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Reservations", description = "지점 예약 관리 API (ADMIN / BRANCH_ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/branches/{id}/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final AdminReservationService adminReservationService;
    private final ReservationService reservationService;
    private final UserRepository userRepository;

    @Operation(summary = "지점 예약 목록 조회", description = "상태 필터/검색어/페이지네이션으로 지점 예약 목록을 조회합니다.")
    @RequireAuth(roles = {"ADMIN", "BRANCH_ADMIN"})
    @GetMapping
    public ApiResponse<?> listReservations(@UserId Long userId, @PathVariable Long id,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(description = "예약번호 또는 고객명 검색") @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        assertBranchAccess(userId, id);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(adminReservationService.listReservations(id, status, q, pageable));
    }

    @Operation(summary = "QR 토큰으로 예약 조회", description = "QR Scan 화면에서 스캔한 토큰으로 지점 예약을 조회합니다.")
    @RequireAuth(roles = {"ADMIN", "BRANCH_ADMIN"})
    @GetMapping("/lookup")
    public ApiResponse<?> lookupByQrToken(@UserId Long userId, @PathVariable Long id, @RequestParam String qrToken) {
        assertBranchAccess(userId, id);
        return ApiResponse.success(adminReservationService.lookupByQrToken(id, qrToken));
    }

    @Operation(summary = "예약 거절", description = "QR Scan 화면의 Reject 버튼 — 예약을 지점 사유로 취소합니다.")
    @RequireAuth(roles = {"ADMIN", "BRANCH_ADMIN"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{reservationId}/reject")
    public void reject(@UserId Long userId, @PathVariable Long id, @PathVariable Long reservationId) {
        assertBranchAccess(userId, id);
        reservationService.rejectByBranch(id, reservationId);
    }

    private void assertBranchAccess(Long userId, Long pathBranchId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));
        if (user.getRole() == Role.BRANCH_ADMIN && !pathBranchId.equals(user.getBranchId())) {
            throw new BusinessException(BusinessErrorCode.NOT_YOUR_BRANCH);
        }
    }
}
