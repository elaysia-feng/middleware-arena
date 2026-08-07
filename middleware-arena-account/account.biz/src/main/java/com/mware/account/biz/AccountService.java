package com.mware.account.biz;

import com.mware.account.domain.AccountBalance;

import java.math.BigDecimal;

/**
 * 账户业务接口。
 * <p>
 * 方法签名已定，具体实现留待接入 account.mapper / Seata AT 后补齐。
 */
public interface AccountService {

    /** 扣减余额（被 order-service 通过 Feign 调用，Seata AT 参与方） */
    void deductBalance(Long userId, BigDecimal amount);

    /** 查询余额 */
    AccountBalance getBalance(Long userId);
}
