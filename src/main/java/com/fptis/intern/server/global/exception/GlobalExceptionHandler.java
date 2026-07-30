package com.fptis.intern.server.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Spring Security를 쓰지 않으므로 인증/인가 예외(BusinessException(UNAUTHORIZED/FORBIDDEN))도
 * EntryPoint가 아니라 이 핸들러를 거쳐 ApiResponse로 나간다 — AuthInterceptor/ArgumentResolver가
 * 던지는 예외도 결국 컨트롤러 호출 경로(DispatcherServlet 내부)라 여기로 잡힌다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        warn(request, "BusinessException", e.getErrorCode().getCode() + " - " + e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        warn(request, "MethodArgumentNotValidException", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        warn(request, "MissingServletRequestParameterException", e.getMessage());
        return ResponseEntity.status(BusinessErrorCode.MISSING_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(BusinessErrorCode.MISSING_INPUT_VALUE));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        warn(request, "MethodArgumentTypeMismatchException", e.getMessage());
        return ResponseEntity.status(BusinessErrorCode.INVALID_TYPE_VALUE.getStatus())
                .body(ApiResponse.error(BusinessErrorCode.INVALID_TYPE_VALUE));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpServletRequest request) {
        warn(request, "HandlerMethodValidationException", e.getMessage());
        return ResponseEntity.status(BusinessErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(BusinessErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolationException(
            ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse(BusinessErrorCode.INVALID_INPUT_VALUE.getMessage());
        warn(request, "ConstraintViolationException", message);
        return ResponseEntity.status(BusinessErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(BusinessErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        warn(request, "HttpMessageNotReadableException", e.getMessage());
        return ResponseEntity.status(BusinessErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(BusinessErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        warn(request, "HttpRequestMethodNotSupportedException", e.getMessage());
        return ResponseEntity.status(BusinessErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.error(BusinessErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFoundException(
            NoResourceFoundException e, HttpServletRequest request) {
        // 정적 리소스(favicon 등) 404는 잡음이 많아 DEBUG로만 남긴다
        log.debug("[{} {}] NoResourceFoundException: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(BusinessErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ApiResponse.error(BusinessErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgumentException(
            IllegalArgumentException e, HttpServletRequest request) {
        warn(request, "IllegalArgumentException", e.getMessage());
        return ResponseEntity.status(BusinessErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(BusinessErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e, HttpServletRequest request) {
        log.error("[{} {}] Unhandled exception", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e));
    }

    private void warn(HttpServletRequest request, String type, String detail) {
        log.warn("[{} {}] {}: {}", request.getMethod(), request.getRequestURI(), type, detail);
    }
}
