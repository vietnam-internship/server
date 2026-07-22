package com.fptis.intern.server.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BusinessErrorCode implements ErrorCode {
    /*
     * 주석 아래로 Service에서 발생하는 ErrorCode 작성
     * 김두현 : C1xx,
     * 이견희 : C2xx,
     * 유지훈 : C3xx
     *
     * xx부분 01부터 순차적으로 작성
     */


    /*
     * 400 BAD_REQUEST: 잘못된 요청
     */
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C002", "잘못된 타입이 입력되었습니다."),
    MISSING_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C003", "필수 입력값이 누락되었습니다."),


    /*
     * 401 UNAUTHORIZED / 403 FORBIDDEN: 인증/인가 공통 (개인 네임스페이스 아님, Axxx)
     */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 구글 인증 정보입니다."),

    /*
     * 405 METHOD_NOT_ALLOWED: 허용되지 않은 Request Method 호출
     */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C007", "허용되지 않은 메서드입니다."),

    /*
     * 이견희 : C2xx (Branch)
     */
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "C201", "존재하지 않는 지점입니다."),

    /*
     * 이견희 : C2xx (Reservation)
     */
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "C202", "존재하지 않는 예약입니다."),
    PHONE_NOT_VERIFIED(HttpStatus.FORBIDDEN, "C203", "휴대폰 인증이 필요합니다."),
    NO_SHOW_LIMIT(HttpStatus.FORBIDDEN, "C204", "노쇼 이력으로 인해 예약이 제한됩니다."),
    STOCK_EXCEEDED(HttpStatus.CONFLICT, "C205", "예약 전용 재고가 부족합니다."),
    TIME_SLOT_FULL(HttpStatus.CONFLICT, "C206", "선택한 픽업 시간대의 예약 정원이 가득 찼습니다."),
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "C207", "이미 취소된 예약입니다."),
    ALREADY_COMPLETED(HttpStatus.CONFLICT, "C208", "이미 완료된 예약입니다."),
    QR_EXPIRED(HttpStatus.CONFLICT, "C209", "QR 코드가 만료되었습니다."),
    QR_ALREADY_USED(HttpStatus.CONFLICT, "C210", "이미 사용되었거나 유효하지 않은 QR 코드입니다."),
    IDENTITY_MISMATCH(HttpStatus.CONFLICT, "C211", "신원 확인에 실패했습니다."),

    /*
     * 409 CONFLICT: 리소스 충돌 (중복 데이터 등)
     */
    ALREADY_EXISTS(HttpStatus.CONFLICT, "C008", "이미 존재하는 데이터입니다."),

    /*
     * 429 TOO_MANY_REQUESTS: 요청 횟수 초과
     */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "C501", "요청 횟수가 초과되었습니다."),

    /*
     * 500 INTERNAL_SERVER_ERROR: 내부 서버 오류
     */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C009", "서버 내부 오류가 발생했습니다."),

    /*
     * 503 SERVICE_UNAVAILABLE: 서비스 일시 불가
     */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "C010", "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public int getStatus() {
        return status.value();
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}