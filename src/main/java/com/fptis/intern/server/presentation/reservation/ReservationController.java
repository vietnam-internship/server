package com.fptis.intern.server.presentation.reservation;

import com.fptis.intern.server.application.reservation.ReservationService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.annotation.UserId;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.presentation.reservation.dto.ReservationCreateRequest;
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

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @RequireAuth
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<?> createReservation(@UserId Long userId, @Valid @RequestBody ReservationCreateRequest request) {
        return ApiResponse.success(reservationService.createReservation(userId, request));
    }

    @RequireAuth
    @GetMapping
    public ApiResponse<?> listMyReservations(@UserId Long userId,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(reservationService.listMyReservations(userId, status, pageable));
    }

    @RequireAuth
    @GetMapping("/{id}")
    public ApiResponse<?> getReservation(@UserId Long userId, @PathVariable Long id) {
        return ApiResponse.success(reservationService.getReservation(userId, id));
    }

    @RequireAuth
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void cancelReservation(@UserId Long userId, @PathVariable Long id) {
        reservationService.cancelReservation(userId, id);
    }
}
