package com.fptis.intern.server.application.reservation;

import com.fptis.intern.server.application.payment.PaymentIntentResult;
import com.fptis.intern.server.application.payment.PaymentService;
import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchTimeSlot;
import com.fptis.intern.server.domain.branch.BranchTimeSlotRepository;
import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.domain.reservation.ReservationStatus;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final BranchCurrencyRateRepository branchCurrencyRateRepository;
    private final BranchTimeSlotRepository branchTimeSlotRepository;
    private final ReservationHoldService reservationHoldService;
    private final PaymentService paymentService;

    /**
     * 이 메서드 자체는 직접 쓰기를 하지 않지만, 클래스 기본값(readOnly=true)을 그대로 두면
     * reservationHoldService.createHold()가 같은 물리 트랜잭션에 join되어 그 안의
     * SELECT ... FOR UPDATE가 "READ ONLY transaction" 에러로 실패한다 — Spring은 REQUIRED
     * propagation으로 참여하는 하위 트랜잭션의 readOnly 속성을 무시하고 바깥쪽(첫 트랜잭션)
     * 설정을 그대로 쓰기 때문이다. 그래서 여기서 명시적으로 쓰기 트랜잭션을 연다.
     */
    @Transactional
    public ReservationDetailResponse createReservation(Long userId, ReservationCreateRequest request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));
        LocalDateTime now = LocalDateTime.now();

        assertNoShowLimitNotExceeded(userId, now);
        assertNoConcurrentPendingPayment(userId);
        // 금액 한도 검증(AMOUNT_LIMIT_EXCEEDED/AMOUNT_BELOW_MINIMUM)은 기준 환율(Currency 도메인)이
        // 있어야 USD/VND 상당액을 계산할 수 있어 이번 범위에서 생략한다 — #26에서 연동 예정.

        Reservation reservation = reservationHoldService.createHold(userId, request, now);

        // Stripe PaymentIntent 생성 실패 시 이 예약은 결제 연결 없는 PENDING_PAYMENT로 남지만,
        // 5분 결제 TTL이 지나면 expireOverduePendingPayments()가 재고/슬롯을 알아서 회수한다.
        PaymentIntentResult intent = paymentService.createPaymentIntent(reservation);

        // 결제 대기(PENDING_PAYMENT) 홀드 생성 알림 발송은 Notification 도메인이 없어 생략한다.
        // QR은 결제 승인 전에는 발급하지 않는다 — PaymentService.handlePaymentSucceeded(웹훅)에서 발급한다.

        Branch branch = getBranchOrThrow(reservation.getBranchId());
        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), reservation.getCurrencyCode())
                .orElse(null);
        return toDetail(reservation, branch, rate, intent.clientSecret());
    }

    public ReservationPageResponse listMyReservations(Long userId, String statusFilter, Pageable pageable) {
        Page<Reservation> reservations = statusFilter == null || statusFilter.isBlank()
                ? reservationRepository.findMyReservations(userId, pageable)
                : reservationRepository.findMyReservationsByStatus(userId, parseStatuses(statusFilter), pageable);

        return ReservationPageResponse.of(reservations.map(this::toSummary));
    }

    public ReservationDetailResponse getReservation(Long userId, Long id) {
        Reservation reservation = getReservationOrThrow(id);
        assertOwner(reservation, userId);

        Branch branch = getBranchOrThrow(reservation.getBranchId());
        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branch.getId(), reservation.getCurrencyCode())
                .orElse(null);
        return toDetail(reservation, branch, rate);
    }

    @Transactional
    public void cancelReservation(Long userId, Long id) {
        Reservation reservation = getReservationOrThrow(id);
        assertOwner(reservation, userId);

        reservation.cancel(false);
        restoreStock(reservation);
        restoreTimeSlot(reservation);

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
            // 여기서 취소/재고·슬롯 복원까지 하면, 뒤이은 QR_EXPIRED 예외(unchecked)가
            // 트랜잭션 전체를 롤백시켜 방금 한 복원 작업까지 함께 사라진다.
            // 실제 정리는 expireOverdueReservations() 스윕러가 전담하고, 여기서는 만료 여부만 판단해 응답한다.
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
                .findRate(branch.getId(), reservation.getCurrencyCode())
                .orElse(null);
        return RedeemResponse.of(reservation, toDetail(reservation, branch, rate));
    }

    @Transactional
    public void expireOverdueReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> overdue = reservationRepository.findExpiredReservations(ReservationStatus.RESERVED, now);
        for (Reservation reservation : overdue) {
            reservation.cancel(true);
            restoreStock(reservation);
            restoreTimeSlot(reservation);
        }
    }

    /**
     * discussion#16: 결제 TTL(5분)을 넘긴 유령 홀드(PENDING_PAYMENT)를 EXPIRED로 풀고 재고/슬롯을
     * 복원한다. 노쇼 이력(autoExpired)과는 분리한다 — 결제까지 가지 않은 홀드는 노쇼가 아니다.
     */
    @Transactional
    public void expireOverduePendingPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> overdue = reservationRepository
                .findExpiredPendingPayments(ReservationStatus.PENDING_PAYMENT, now);
        for (Reservation reservation : overdue) {
            reservation.expireHold();
            restoreStock(reservation);
            restoreTimeSlot(reservation);
        }
    }

    private void restoreStock(Reservation reservation) {
        branchCurrencyRateRepository.findForUpdate(reservation.getBranchId(), reservation.getCurrencyCode())
                .ifPresent(rate -> rate.increaseStock(reservation.getAmount()));
    }

    private void restoreTimeSlot(Reservation reservation) {
        branchTimeSlotRepository
                .lockForUpdate(reservation.getBranchId(), reservation.getPickupDate(), reservation.getPickupTime())
                .ifPresent(BranchTimeSlot::increaseRemaining);
    }

    /**
     * PRD §19.2: 최근 30일 노쇼 1회 → 동시 RESERVED 1건 제한, 누적 3회 이상이면 가장 최근
     * 노쇼로부터 7일간 신규 예약을 차단한다.
     */
    private void assertNoShowLimitNotExceeded(Long userId, LocalDateTime now) {
        long noShowCount = reservationRepository.countNoShowsSince(userId, ReservationStatus.CANCELLED, now.minusDays(30));

        if (noShowCount >= 3) {
            LocalDateTime sevenDaysAgo = now.minusDays(7);
            boolean blockedByRecentNoShow = reservationRepository
                    .findNoShows(userId, ReservationStatus.CANCELLED, PageRequest.of(0, 1))
                    .stream().findFirst()
                    .map(r -> r.getUpdatedAt().isAfter(sevenDaysAgo))
                    .orElse(false);
            if (blockedByRecentNoShow) {
                throw new BusinessException(BusinessErrorCode.NO_SHOW_LIMIT);
            }
        } else if (noShowCount >= 1) {
            // 활성 예약 수를 세는 시점과 예약을 생성하는 시점 사이의 레이스를 막기 위해,
            // 카운트를 읽기 전 유저 행에 락을 걸어 같은 유저의 동시 요청을 직렬화한다.
            // 이 락은 트랜잭션이 끝날 때까지 유지되므로, 뒤이은 재고/슬롯 락(findForUpdate/lockForUpdate)과의
            // 데드락을 피하려면 항상 유저 락을 먼저 잡아야 한다.
            userRepository.findForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));
            // MySQL REPEATABLE READ에서 일반 SELECT는 유저 락 대기 전 스냅샷을 읽어 방금 커밋된
            // 예약을 놓칠 수 있어, 최신 커밋 데이터를 보장하는 락 획득 읽기로 확인한다.
            boolean hasActiveReservation = !reservationRepository
                    .findActiveReservationsForUpdate(userId, ReservationStatus.RESERVED, now)
                    .isEmpty();
            if (hasActiveReservation) {
                throw new BusinessException(BusinessErrorCode.NO_SHOW_LIMIT);
            }
        }
    }

    /**
     * discussion#16 유령 홀드 완화책: 인증된 사용자당 동시 PENDING_PAYMENT 1건 제한.
     */
    private void assertNoConcurrentPendingPayment(Long userId) {
        long pendingCount = reservationRepository.countByUserIdAndStatus(userId, ReservationStatus.PENDING_PAYMENT);
        if (pendingCount > 0) {
            throw new BusinessException(BusinessErrorCode.CONCURRENT_PENDING_PAYMENT_LIMIT);
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
        return toDetail(reservation, branch, rate, null);
    }

    private ReservationDetailResponse toDetail(Reservation reservation, Branch branch, BranchCurrencyRate rate,
                                                String paymentClientSecret) {
        Double preferentialRate = rate != null ? rate.getPreferentialRate() : null;
        boolean reservationAvailable = rate != null && rate.hasStock();
        BranchSummaryResponse branchSummary = BranchSummaryResponse.of(branch, null,
                branch.isOpenNow(LocalDateTime.now()), preferentialRate, reservationAvailable);
        return ReservationDetailResponse.of(reservation, branchSummary, paymentClientSecret);
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

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
