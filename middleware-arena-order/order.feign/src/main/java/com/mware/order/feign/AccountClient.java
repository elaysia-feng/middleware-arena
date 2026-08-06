package com.mware.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调用 account-service 的 OpenFeign 客户端（占位）。
 * <p>
 * TODO[Seata 分布式事务]：下单时调用扣余额接口，接入 Seata AT 后
 * 需把 order.feign 依赖引入 order.web / order.biz，
 * 并在启动类加 @EnableFeignClients。
 */
@FeignClient(name = "account-service")
public interface AccountClient {

    /**
     * 扣减余额（占位）。
     *
     * @param userId 用户 ID
     * @param amount 扣减金额
     * @return 扣减结果
     */
    @PostMapping("/account/deduct")
    String deductBalance(@RequestParam("userId") Long userId,
                         @RequestParam("amount") java.math.BigDecimal amount);
}
