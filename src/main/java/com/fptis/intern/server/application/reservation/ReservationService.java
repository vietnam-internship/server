package com.fptis.intern.server.application.reservation;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.branch.dto.BranchSummaryResponse;
import com.fptis.intern.server.presentation.reservation.dto.RedeemRequest;
import com.fptis.intern.server.presentation.reservation.dto.RedeemResponse;
import com.fptis.intern.server.presentation.reservation.dto.ReservationCreateRequest;
import com.fptis.intern.server.presentation.reservation.dto.ReservationDetailResponse;
import com.fptis.intern.server.presentation.reservation.dto.ReservationPageResponse;
import com.fptis.intern.server.presentation.reservation.dto.ReservationSummaryResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private static final DateTimeFormatter RESERVATION_NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final BranchCurrencyRateRepository branchCurrencyRateRepository;

    @Transactional
    public ReservationDetailResponse createReservation(Long userId, ReservationCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));
        LocalDateTime now = LocalDateTime.now();

        if (!user.isPhoneVerified()) {
            throw new BusinessException(BusinessErrorCode.PHONE_NOT_VERIFIED);
        }
        assertNoShowLimitNotExceeded(userId, now);
        // 금액 한도 검증(AMOUNT_LIMIT_EXCEEDED/AMOUNT_BELOW_MINIMUM)은 기준 환율(Currency 도메인)이
        // 있어야 USD/VND 상당액을 계산할 수 있어 이번 범위에서 생략한다 — #26에서 연동 예정.

        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BRANCH_NOT_FOUND));

        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findForUpdate(branch.getId(), request.currencyCode())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.STOCK_EXCEEDED));
        rate.decreaseStock(request.amount());

        LocalTime pickupTime = LocalTime.parse(request.pickupTime());
        long slotCount = reservationRepository.countByBranchIdAndPickupDateAndPickupTimeAndStatusAndExpiresAtAfter(
                branch.getId(), request.pickupDate(), pickupTime, ReservationStatus.RESERVED, now);
        if (slotCount >= branch.getTimeSlotCapacity()) {
            throw new BusinessException(BusinessErrorCode.TIME_SLOT_FULL);
        }

        Reservation reservation = Reservation.builder()
                .userId(userId)
                .branchId(branch.getId())
                .currencyCode(request.currencyCode())
                .amount(request.amount())
                .pickupDate(request.pickupDate())
                .pickupTime(pickupTime)
                .now(now)
                .build();
        reservationRepository.save(reservation);

        reservation.assignReservationNumber(generateReservationNumber(reservation.getId(), now));
        reservation.issueQrToken(generateQrToken());

        // 예약 완료 알림 발송은 Notification 도메인이 없어 생략한다.

        return toDetail(reservation, branch, rate);
    }

    public ReservationPageResponse listMyReservations(Long userId, String statusFilter, Pageable pageable) {
        Page<Reservation> reservations = statusFilter == null || statusFilter.isBlank()
                ? reservationRepository.findByUserId(userId, pageable)
                : reservationRepository.findByUserIdAndStatusIn(userId, parseStatuses(statusFilter), pageable);

        return ReservationPageResponse.of(reservations.map(this::toSummary));
    }

    public ReservationDetailResponse getReservation(Long userId, Long id) {
        Reservation reservation = getReservationOrThrow(id);
        assertOwner(reservation, userId);

        Branch branch = getBranchOrThrow(reservation.getBranchId());
        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findByBranchIdAndCurrencyCode(branch.getId(), reservation.getCurrencyCode())
                .orElse(null);
        return toDetail(reservation, branch, rate);
    }

    @Transactional
    public void cancelReservation(Long userId, Long id) {
        Reservation reservation = getReservationOrThrow(id);
        assertOwner(reservation, userId);

        reservation.cancel(false);
        restoreStock(reservation);

        // 취소 알림 발송은 Notification 도메인이 없어 생략한다.
    }

    @Transactional
    public RedeemResponse redeem(Long branchId, Long reservationId, RedeemRequest request) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.getBranchId().equals(branchId)) {
            throw new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        if (reservation.isExpired(now)) {
            reservation.cancel(true);
            restoreStock(reservation);
            throw new BusinessException(BusinessErrorCode.QR_EXPIRED);
        }
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new BusinessException(BusinessErrorCode.ALREADY_COMPLETED);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException(BusinessErrorCode.QR_ALREADY_USED);
        }
        if (!Boolean.TRUE.equals(request.idVerified())) {
            throw new BusinessException(BusinessErrorCode.IDENTITY_MISMATCH);
        }
        if (reservation.getQrToken() == null || !constantTimeEquals(reservation.getQrToken(), request.qrToken())) {
            throw new BusinessException(BusinessErrorCode.QR_ALREADY_USED);
        }

        reservation.complete(now);

        Branch branch = getBranchOrThrow(reservation.getBranchId());
        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findByBranchIdAndCurrencyCode(branch.getId(), reservation.getCurrencyCode())
                .orElse(null);
        return RedeemResponse.of(reservation, toDetail(reservation, branch, rate));
    }

    @Transactional
    public void expireOverdueReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> overdue =
                reservationRepository.findAllByStatusAndExpiresAtBefore(ReservationStatus.RESERVED, now);
        for (Reservation reservation : overdue) {
            reservation.cancel(true);
            restoreStock(reservation);
        }
    }

    private void restoreStock(Reservation reservation) {
        branchCurrencyRateRepository.findForUpdate(reservation.getBranchId(), reservation.getCurrencyCode())
                .ifPresent(rate -> rate.increaseStock(reservation.getAmount()));
    }

    /**
     * PRD §19.2: 최근 30일 노쇼 1회 → 동시 RESERVED 1건 제한, 누적 3회 이상이면 가장 최근
     * 노쇼로부터 7일간 신규 예약을 차단한다.
     */
    private void assertNoShowLimitNotExceeded(Long userId, LocalDateTime now) {
        long noShowCount = reservationRepository.countByUserIdAndStatusAndAutoExpiredTrueAndUpdatedAtAfter(
                userId, ReservationStatus.CANCELLED, now.minusDays(30));

        if (noShowCount >= 3) {
            LocalDateTime sevenDaysAgo = now.minusDays(7);
            boolean blockedByRecentNoShow = reservationRepository
                    .findTopByUserIdAndStatusAndAutoExpiredTrueOrderByUpdatedAtDesc(userId, ReservationStatus.CANCELLED)
                    .map(r -> r.getUpdatedAt().isAfter(sevenDaysAgo))
                    .orElse(false);
            if (blockedByRecentNoShow) {
                throw new BusinessException(BusinessErrorCode.NO_SHOW_LIMIT);
            }
        } else if (noShowCount >= 1) {
            long activeCount = reservationRepository.countByUserIdAndStatusAndExpiresAtAfter(
                    userId, ReservationStatus.RESERVED, now);
            if (activeCount >= 1) {
                throw new BusinessException(BusinessErrorCode.NO_SHOW_LIMIT);
            }
        }
    }

    private void assertOwner(Reservation reservation, Long userId) {
        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN);
        }
    }

    private Reservation getReservationOrThrow(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.RESERVATION_NOT_FOUND));
    }

    private Branch getBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BRANCH_NOT_FOUND));
    }

    private ReservationSummaryResponse toSummary(Reservation reservation) {
        String branchName = branchRepository.findById(reservation.getBranchId())
                .map(Branch::getName)
                .orElse(null);
        return ReservationSummaryResponse.of(reservation, branchName);
    }

    private ReservationDetailResponse toDetail(Reservation reservation, Branch branch, BranchCurrencyRate rate) {
        Double preferentialRate = rate != null ? rate.getPreferentialRate() : null;
        boolean reservationAvailable = rate != null && rate.hasStock();
        BranchSummaryResponse branchSummary = BranchSummaryResponse.of(branch, null,
                branch.isOpenNow(LocalDateTime.now()), preferentialRate, reservationAvailable);
        return ReservationDetailResponse.of(reservation, branchSummary);
    }

    private List<ReservationStatus> parseStatuses(String statusFilter) {
        try {
            return Arrays.stream(statusFilter.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(ReservationStatus::valueOf)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BusinessErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String generateReservationNumber(Long id, LocalDateTime now) {
        return "TX-" + RESERVATION_NUMBER_DATE.format(now) + "-" + String.format("%04d", id % 10_000);
    }

    private String generateQrToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
