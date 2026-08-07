package com.mware.order.feign;

import com.mware.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 调用 account-service 的 OpenFeign 客户端（框架占位）。
 * <p>
 * TODO[Seata 分布式事务]：下单时调用扣余额接口，接入 Seata AT 后
 * 需把 order.feign 依赖引入 order.biz，并在启动类加 @EnableFeignClients。
 */
@FeignClient(name = "account-service")
public interface AccountClient {

    /**
     * 扣减余额。
     *
     * @param userId 用户 ID
     * @param amount 扣减金额
     * @return 扣减结果（余额不足时 account 侧抛 ApiException，Feign 透传）
     */
    @PostMapping("/account/deduct")
    ApiResponse<Void> deductBalance(@RequestParam("userId") Long userId,
                                    @RequestParam("amount") BigDecimal amount);
}
