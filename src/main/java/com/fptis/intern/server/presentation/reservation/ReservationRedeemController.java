package com.fptis.intern.server.presentation.reservation;

import com.fptis.intern.server.application.reservation.ReservationService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.reservation.dto.RedeemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * URL은 /branches 하위지만(PRD §8 Cash Pickup) 실제로는 Reservation 상태 전이(RESERVED→COMPLETED)라
 * Branch가 아닌 Reservation 도메인 패키지에 둔다.
 */
@Tag(name = "Reservation", description = "환전 예약 API (로그인 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/branches/{id}/reservations")
@RequiredArgsConstructor
public class ReservationRedeemController {

    private final ReservationService reservationService;

    @Operation(summary = "예약 수령 처리 (Redeem)", description = "지점 창구에서 QR 코드와 신원 확인 결과로 예약된 환전을 수령 완료 처리합니다. ADMIN 권한이 필요합니다.")
    @RequireAuth(roles = "ADMIN")
    @PostMapping("/{reservationId}/redeem")
    public ApiResponse<?> redeem(@Parameter(description = "지점 ID") @PathVariable Long id,
                                  @Parameter(description = "예약 ID") @PathVariable Long reservationId,
                                  @Valid @RequestBody RedeemRequest request) {
        return ApiResponse.success(reservationService.redeem(id, reservationId, request));
    }
}
