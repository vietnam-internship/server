package com.fptis.intern.server.application.reservation;

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
import com.fptis.intern.server.global.config.ReservationTimingProperties;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.reservation.dto.ReservationCreateRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 단위 불변식 확인(노쇼 제한, 동시 결제대기 제한) + 재고/슬롯 비관적 락 + Reservation insert를
 * 하나의 트랜잭션으로 묶는다 — `docs/discussion-reservation-transaction-boundary.md`의 Phase 1.
 * Stripe 호출처럼 외부 네트워크가 끼는 로직(PaymentService)과는 트랜잭션 경계를 분리하는 것이 이
 * 클래스의 목적이다 — discussion#16이 방안 1(Combined)을 버린 이유가 "PG 응답을 기다리는 동안 슬롯
 * 락을 들고 있는 것"이었으므로, 이 클래스의 @Transactional 메서드가 커밋된 뒤에만(락이 풀린
 * 뒤에만) 결제 관련 호출을 이어가야 한다. {@code ReservationService.createReservation}은 더 이상
 * 이 메서드를 감싸는 트랜잭션을 열지 않는다 — 그러면 아래 락들이 Stripe 호출까지 물려버린다.
 *
 * <p>유저 단위 불변식(노쇼 1~2회 시 활성 RESERVED 1건 제한, 동시 PENDING_PAYMENT 1건 제한)은
 * 검증 단계(Phase 0)가 아니라 여기서 확인한다 — "유저 행 락 획득 → 조건 확인 → Reservation
 * insert"가 같은 트랜잭션 안에서 끊기지 않아야 체크와 생성 사이의 TOCTOU 레이스가 안 생기기
 * 때문이다(discussion-reservation-transaction-boundary.md 방안 1).
 */
@Service
@RequiredArgsConstructor
public class ReservationHoldService {

    private static final DateTimeFormatter RESERVATION_NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final BranchCurrencyRateRepository branchCurrencyRateRepository;
    private final BranchTimeSlotRepository branchTimeSlotRepository;
    private final ReservationTimingProperties timingProperties;

    /**
     * @param lockedRate Phase 0(ReservationService)에서 검증용으로 먼저 읽은 환율로 계산한 확정
     *                   환산 기준값 — 이 트랜잭션 안에서 Reservation에 바로 고정해야, 커밋 뒤
     *                   별도 UPDATE 없이 한 번의 insert로 끝난다.
     */
    @Transactional
    public Reservation createHold(Long userId, ReservationCreateRequest request, LocalDateTime now, double lockedRate) {
        assertReservationEligibility(userId, now);

        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BRANCH_NOT_FOUND));

        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findForUpdate(branch.getId(), request.currencyCode())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.STOCK_EXCEEDED));
        rate.decreaseStock(request.amount());

        LocalTime pickupTime = LocalTime.parse(request.pickupTime());
        lockTimeSlot(branch, request.pickupDate(), pickupTime).decreaseRemaining();

        Reservation reservation = Reservation.builder()
                .userId(userId)
                .branchId(branch.getId())
                .currencyCode(request.currencyCode())
                .amount(request.amount())
                .pickupDate(request.pickupDate())
                .pickupTime(pickupTime)
                .now(now)
                .paymentHoldMinutes(timingProperties.paymentHoldMinutes())
                .build();
        reservation.assignLockedRate(lockedRate);
        reservationRepository.save(reservation);
        reservation.assignReservationNumber(generateReservationNumber(reservation.getId(), now));
        return reservation;
    }

    /**
     * PRD §19.2(노쇼 1회 → 동시 RESERVED 1건 제한, 누적 3회 이상 7일 차단)와 discussion#16
     * 유령 홀드 완화책(동시 PENDING_PAYMENT 1건 제한)을 함께 확인한다.
     * (ReservationService에서 옮겨옴 — discussion-reservation-transaction-boundary.md 방안 1.
     * 기존에는 동시 PENDING_PAYMENT 체크에 락이 전혀 없어 레이스가 가능했는데
     * (reservation-payment-review.md ③), 이번에 유저 락 획득을 노쇼 유무와 무관하게 항상 먼저
     * 하도록 합쳐서 그 레이스도 같이 막는다.)
     */
    private void assertReservationEligibility(Long userId, LocalDateTime now) {
        long noShowCount = reservationRepository.countNoShowsSince(userId, ReservationStatus.CANCELLED, now.minusDays(30));
        if (noShowCount >= 3 && blockedByRecentNoShow(userId, now)) {
            throw new BusinessException(BusinessErrorCode.NO_SHOW_LIMIT);
        }

        // 유저 행 락 — 노쇼 제한과 동시 PENDING_PAYMENT 제한을 모두 이 락 아래에서 확인해야
        // "체크 시점과 예약 생성 시점 사이"의 레이스가 안 생긴다. 이 락은 이 트랜잭션이 끝날
        // 때까지 유지되므로, 뒤이은 재고/슬롯 락(findForUpdate/lockForUpdate)과의 데드락을
        // 피하려면 항상 유저 락을 먼저 잡아야 한다.
        userRepository.findForUpdate(userId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));

        if (noShowCount >= 1) {
            // MySQL REPEATABLE READ에서 일반 SELECT는 유저 락 대기 전 스냅샷을 읽어 방금 커밋된
            // 예약을 놓칠 수 있어, 최신 커밋 데이터를 보장하는 락 획득 읽기로 확인한다.
            boolean hasActiveReservation = !reservationRepository
                    .findActiveReservationsForUpdate(userId, ReservationStatus.RESERVED, now)
                    .isEmpty();
            if (hasActiveReservation) {
                throw new BusinessException(BusinessErrorCode.NO_SHOW_LIMIT);
            }
        }

        boolean hasPendingPayment = !reservationRepository
                .findByUserIdAndStatusForUpdate(userId, ReservationStatus.PENDING_PAYMENT)
                .isEmpty();
        if (hasPendingPayment) {
            throw new BusinessException(BusinessErrorCode.CONCURRENT_PENDING_PAYMENT_LIMIT);
        }
    }

    private boolean blockedByRecentNoShow(Long userId, LocalDateTime now) {
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        return reservationRepository
                .findNoShows(userId, ReservationStatus.CANCELLED, PageRequest.of(0, 1))
                .stream().findFirst()
                .map(r -> r.getUpdatedAt().isAfter(sevenDaysAgo))
                .orElse(false);
    }

    /**
     * discussions#13에서 채택한 "방안 A(1 Row Locked)" — 슬롯 행을 없으면 만들고(ensureExists),
     * 그 행에 비관적 락을 걸어 반환한다. 호출자는 반환된 행에서 곧바로 increase/decreaseRemaining을
     * 호출해야 같은 트랜잭션 안에서 락이 유지된 채로 원자적으로 반영된다.
     */
    private BranchTimeSlot lockTimeSlot(Branch branch, LocalDate pickupDate, LocalTime pickupTime) {
        branchTimeSlotRepository.ensureExists(branch.getId(), pickupDate, pickupTime, branch.getTimeSlotCapacity());
        return branchTimeSlotRepository.lockForUpdate(branch.getId(), pickupDate, pickupTime)
                .orElseThrow(() -> new IllegalStateException("ensureExists 직후이므로 슬롯 행은 항상 존재해야 한다."));
    }

    private String generateReservationNumber(Long id, LocalDateTime now) {
        return "TX-" + RESERVATION_NUMBER_DATE.format(now) + "-" + String.format("%04d", id % 10_000);
    }
}
