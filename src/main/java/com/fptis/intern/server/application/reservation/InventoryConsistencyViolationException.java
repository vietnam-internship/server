package com.fptis.intern.server.application.reservation;

/**
 * TimeSlotInventoryReconciler가 remaining > capacity(낙관적 락이 지키는 불변식 위반)를 발견했을 때
 * 던진다. GlobalExceptionHandler(@RestControllerAdvice)는 컨트롤러 예외만 잡고 이 예외는
 * 스케줄러(백그라운드 스레드)에서 발생해 그 경로를 타지 않으므로, BusinessException/BusinessErrorCode가
 * 아니라 이 예외 전용으로 만들어 ReservationExpirySweeper가 직접 try/catch로 잡아 알림에 연결한다.
 */
public class InventoryConsistencyViolationException extends RuntimeException {

    public InventoryConsistencyViolationException(int violationCount) {
        super("재고 정합성 위반 감지: " + violationCount + "건 (remaining > capacity)");
    }
}
