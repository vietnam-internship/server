package com.fptis.intern.server.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String message;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.message = errorCode.getMessage();
    }

    public BusinessException(String message, ErrorCode errorCode) {
        super(message);
        this.message = message;
        this.errorCode = errorCode;
    }
}