package com.fptis.intern.server.presentation.reservation;

import com.fptis.intern.server.application.reservation.ReservationService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.annotation.UserId;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.reservation.dto.ReservationCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reservation", description = "환전 예약 API (로그인 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "환전 예약 생성", description = "지점, 통화, 금액, 픽업 일시(30분 슬롯)로 환전 예약을 생성합니다.")
    @RequireAuth
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<?> createReservation(@UserId Long userId, @Valid @RequestBody ReservationCreateRequest request) {
        return ApiResponse.success(reservationService.createReservation(userId, request));
    }

    @Operation(summary = "내 예약 목록 조회", description = "로그인한 사용자의 예약 목록을 상태별/페이지 단위로 조회합니다.")
    @RequireAuth
    @GetMapping
    public ApiResponse<?> listMyReservations(@UserId Long userId,
                                              @Parameter(description = "예약 상태 필터 (예: RESERVED, COMPLETED, CANCELLED)") @RequestParam(required = false) String status,
                                              @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
                                              @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(reservationService.listMyReservations(userId, status, pageable));
    }

    @Operation(summary = "예약 상세 조회", description = "예약 ID로 본인 예약의 상세 정보를 조회합니다.")
    @RequireAuth
    @GetMapping("/{id}")
    public ApiResponse<?> getReservation(@UserId Long userId, @Parameter(description = "예약 ID") @PathVariable Long id) {
        return ApiResponse.success(reservationService.getReservation(userId, id));
    }

    @Operation(summary = "예약 취소", description = "본인의 예약을 취소합니다.")
    @RequireAuth
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void cancelReservation(@UserId Long userId, @Parameter(description = "예약 ID") @PathVariable Long id) {
        reservationService.cancelReservation(userId, id);
    }
}
