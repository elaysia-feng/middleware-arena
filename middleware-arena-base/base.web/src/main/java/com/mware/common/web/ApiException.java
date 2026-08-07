package com.mware.common.web;

import lombok.Getter;

/**
 * 业务异常：业务代码抛出后由 {@link GlobalExceptionHandler} 统一转为 Result。
 */
@Getter
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(String message) {
        this(400, message);
    }

    public ApiException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }
}
