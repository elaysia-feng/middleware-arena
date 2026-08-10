package com.mware.account.feign;

import com.mware.account.domain.AccountBalance;
import com.mware.common.web.ApiResponse;

/**
 * 账户 HTTP 契约（被调方侧，仅定义端点签名）。
 * <p>
 * account 是被调方，真正的 Feign 客户端由调用方（order-service 的 order.feign）定义；
 * 此接口用于对齐端点契约（POST /account/deduct、GET /account/balance/{userId}），
 * 返回类型统一为 {@link ApiResponse}。
 */
public interface AccountApi {

    /**
     * 扣减余额（对应 POST /account/deduct）。
     *
     * @param userId 用户 ID
     * @param amount 扣减金额（单位：分）
     * @return 扣减结果（余额不足时抛 ApiException(BALANCE_NOT_ENOUGH)）
     */
    ApiResponse<Void> deductBalance(Long userId, Long amount);

    /**
     * 查询余额（对应 GET /account/balance/{userId}）。
     *
     * @param userId 用户 ID
     * @return 账户余额
     */
    ApiResponse<AccountBalance> getBalance(Long userId);
}
