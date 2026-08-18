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

    /**
     * ErrorCode + 自定义消息：复用 errorCode 的业务码，但覆盖默认错误描述。
     * 适用于"同样是 NOT_FOUND，但具体资源不同时给出更精确文案"的场景。
     */
    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), message);
    }
}
