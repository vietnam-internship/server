package com.fptis.intern.server.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final ResultType result;
    private final T data;
    private String code;
    private String message;

    public static ApiResponse<?> success() {
        return new ApiResponse<>(ResultType.SUCCESS, null, null, null);
    }

    public static <S> ApiResponse<S> success(S data) {
        return new ApiResponse<>(ResultType.SUCCESS, data, null, null);
    }

    public static <S> ApiResponse<S> success(S data, String message) {
        return new ApiResponse<>(ResultType.SUCCESS, data, null, message);
    }

    public static ApiResponse<?> error(ErrorCode error) {
        return new ApiResponse<>(ResultType.FAIL, null, error.getCode(), error.getMessage());
    }

    public static ApiResponse<?> error(MethodArgumentNotValidException error) {
        String message = Stream.concat(
                        error.getBindingResult().getFieldErrors().stream()
                                .map(e -> e.getField() + ": " + e.getDefaultMessage()),
                        error.getBindingResult().getGlobalErrors().stream()
                                .map(e -> e.getObjectName() + ": " + e.getDefaultMessage())
                )
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = BusinessErrorCode.INVALID_INPUT_VALUE.getMessage();
        }
        return new ApiResponse<>(ResultType.FAIL, null, BusinessErrorCode.INVALID_INPUT_VALUE.getCode(), message);
    }

    // 예외 메시지를 그대로 노출하면 내부 구현이 유출될 수 있어 공통 코드/메시지로 응답
    public static ApiResponse<?> error(Exception error) {
        return error(BusinessErrorCode.INTERNAL_SERVER_ERROR);
    }
}