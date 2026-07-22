package com.fptis.intern.server.presentation.reservation;

import com.fptis.intern.server.application.reservation.ReservationService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.reservation.dto.RedeemRequest;
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
@RestController
@RequestMapping("/branches/{id}/reservations")
@RequiredArgsConstructor
public class ReservationRedeemController {

    private final ReservationService reservationService;

    @RequireAuth(roles = "ADMIN")
    @PostMapping("/{reservationId}/redeem")
    public ApiResponse<?> redeem(@PathVariable Long id, @PathVariable Long reservationId,
                                  @Valid @RequestBody RedeemRequest request) {
        return ApiResponse.success(reservationService.redeem(id, reservationId, request));
    }
}
