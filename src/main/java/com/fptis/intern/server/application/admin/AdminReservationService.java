package com.fptis.intern.server.application.admin;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.admin.AdminReservationStatusMapper;
import com.fptis.intern.server.presentation.admin.dto.AdminReservationDetailResponse;
import com.fptis.intern.server.presentation.admin.dto.AdminReservationListResponse;
import com.fptis.intern.server.presentation.admin.dto.AdminReservationSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    public AdminReservationListResponse listReservations(Long branchId, String statusFilter, String q, Pageable pageable) {
        List<ReservationStatus> statuses = resolveStatuses(statusFilter);
        Page<Reservation> reservations = reservationRepository.findBranchReservations(branchId, statuses,
                (q == null || q.isBlank()) ? null : q.trim(), pageable);

        Map<Long, String> userNames = userNamesFor(reservations.getContent());
        Page<AdminReservationSummaryResponse> mapped = reservations.map(r -> toSummary(r, userNames));
        return AdminReservationListResponse.of(mapped);
    }

    public AdminReservationDetailResponse lookupByQrToken(Long branchId, String qrToken) {
        Reservation reservation = reservationRepository.findByBranchIdAndQrToken(branchId, qrToken)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND));
        return toDetail(reservation);
    }

    private AdminReservationSummaryResponse toSummary(Reservation r, Map<Long, String> userNames) {
        return new AdminReservationSummaryResponse(r.getId(), r.getReservationNumber(),
                userNames.getOrDefault(r.getUserId(), "Unknown"), r.getCurrencyCode() + " → KRW",
                r.getAmount(), AdminReservationStatusMapper.toAdminBucket(r.getStatus()));
    }

    private AdminReservationDetailResponse toDetail(Reservation r) {
        String branchName = branchRepository.findById(r.getBranchId()).map(Branch::getName).orElse(null);
        String customerName = userRepository.findById(r.getUserId()).map(User::getName).orElse("Unknown");
        String pickupDetail = "Pickup " + r.getPickupTime() + " " + r.getPickupDate();
        return new AdminReservationDetailResponse(r.getId(), r.getReservationNumber(), customerName,
                r.getCurrencyCode() + " → KRW", r.getAmount(), AdminReservationStatusMapper.toAdminBucket(r.getStatus()),
                branchName, pickupDetail);
    }

    private Map<Long, String> userNamesFor(List<Reservation> reservations) {
        List<Long> userIds = reservations.stream().map(Reservation::getUserId).distinct().toList();
        return userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getName));
    }

    private List<ReservationStatus> resolveStatuses(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank() || statusFilter.equalsIgnoreCase("ALL")) {
            return List.of(ReservationStatus.RESERVED, ReservationStatus.COMPLETED,
                    ReservationStatus.CANCELLED, ReservationStatus.EXPIRED);
        }
        return switch (statusFilter.toUpperCase()) {
            case "PENDING" -> List.of(ReservationStatus.RESERVED);
            case "COMPLETED" -> List.of(ReservationStatus.COMPLETED);
            case "CANCELLED" -> List.of(ReservationStatus.CANCELLED, ReservationStatus.EXPIRED);
            default -> throw new BusinessException(BusinessErrorCode.INVALID_INPUT_VALUE);
        };
    }
}
