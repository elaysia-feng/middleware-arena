package com.mware.common.web;

import lombok.Getter;

/**
 * 统一错误码：业务代码抛出 {@link ApiException} 时引用，由 {@link GlobalExceptionHandler} 转为响应。
 * <p>
 * code 约定：HTTP 语义段 + 业务细分（如 40401 = 订单相关资源不存在，409 段 = 业务冲突/余额库存不足）。
 */
@Getter
public enum ErrorCode {

    PARAM_INVALID(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),

    // 订单域
    ORDER_NOT_FOUND(40401, "订单不存在"),

    // 商品域
    PRODUCT_NOT_FOUND(40402, "商品不存在"),

    // 分布式事务链路：库存 / 余额不足
    STOCK_NOT_ENOUGH(40901, "库存不足"),
    BALANCE_NOT_ENOUGH(40902, "余额不足"),

    INTERNAL_ERROR(500, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
