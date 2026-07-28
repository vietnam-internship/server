package com.fptis.intern.server.application.admin;

import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.domain.reservation.ReservationRepository.CurrencyCountProjection;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.presentation.admin.AdminReservationStatusMapper;
import com.fptis.intern.server.presentation.admin.dto.AdminDashboardResponse;
import com.fptis.intern.server.presentation.admin.dto.AdminPopularCurrencyResponse;
import com.fptis.intern.server.presentation.admin.dto.AdminRecentReservationResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final int RECENT_LIMIT = 5;
    private static final int POPULAR_CURRENCY_LIMIT = 5;

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public AdminDashboardResponse getDashboard(Long branchId) {
        long totalUsers = userRepository.countByRole(Role.USER);
        long pendingCount = branchId != null
                ? reservationRepository.countByBranchIdAndStatus(branchId, ReservationStatus.RESERVED)
                : reservationRepository.countByStatus(ReservationStatus.RESERVED);

        Pageable popularLimit = PageRequest.of(0, POPULAR_CURRENCY_LIMIT);
        List<CurrencyCountProjection> popular = branchId != null
                ? reservationRepository.findPopularCurrenciesByBranch(branchId, popularLimit)
                : reservationRepository.findPopularCurrencies(popularLimit);

        Pageable recentLimit = PageRequest.of(0, RECENT_LIMIT);
        List<Reservation> recent = branchId != null
                ? reservationRepository.findRecentReservationsByBranch(branchId, recentLimit)
                : reservationRepository.findRecentReservations(recentLimit);

        Map<Long, String> userNames = userRepository.findAllById(
                        recent.stream().map(Reservation::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        List<AdminRecentReservationResponse> recentResponses = recent.stream()
                .map(r -> new AdminRecentReservationResponse(
                        userNames.getOrDefault(r.getUserId(), "Unknown"),
                        r.getCurrencyCode() + " → KRW",
                        r.getAmount(),
                        AdminReservationStatusMapper.toAdminBucket(r.getStatus())))
                .toList();

        List<AdminPopularCurrencyResponse> popularResponses = popular.stream()
                .map(p -> new AdminPopularCurrencyResponse(p.getCurrencyCode(), p.getCount()))
                .toList();

        return new AdminDashboardResponse(totalUsers, pendingCount, popularResponses, recentResponses);
    }
}
