package com.fptis.intern.server.application.reservation;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchTimeSlot;
import com.fptis.intern.server.domain.branch.BranchTimeSlotRepository;
import com.fptis.intern.server.domain.reservation.Reservation;
import com.fptis.intern.server.domain.reservation.ReservationRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.reservation.dto.ReservationCreateRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고/슬롯에 비관적 락을 거는 부분만 이 클래스에 모은다 — Stripe 호출처럼 외부 네트워크가 끼는
 * 로직(PaymentService)과 트랜잭션 경계를 분리하기 위한 것이 유일한 목적이다. discussion#16이
 * 방안 1(Combined)을 버린 이유가 "PG 응답을 기다리는 동안 슬롯 락을 들고 있는 것"이었으므로,
 * 이 클래스의 @Transactional 메서드가 커밋된 뒤에만(락이 풀린 뒤에만) 결제 관련 호출을 이어가야 한다.
 */
@Service
@RequiredArgsConstructor
public class ReservationHoldService {

    private static final DateTimeFormatter RESERVATION_NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReservationRepository reservationRepository;
    private final BranchRepository branchRepository;
    private final BranchCurrencyRateRepository branchCurrencyRateRepository;
    private final BranchTimeSlotRepository branchTimeSlotRepository;

    @Transactional
    public Reservation createHold(Long userId, ReservationCreateRequest request, LocalDateTime now) {
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
                .build();
        reservationRepository.save(reservation);
        reservation.assignReservationNumber(generateReservationNumber(reservation.getId(), now));
        return reservation;
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
