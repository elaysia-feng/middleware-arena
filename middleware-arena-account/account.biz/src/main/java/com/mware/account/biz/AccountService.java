package com.mware.account.biz;

import com.mware.account.domain.AccountBalance;

/**
 * 账户业务接口。
 * <p>
 * 扣余额接口作为 Seata AT 分支事务参与下单链路。
 * 金额统一单位：分（Long）。
 */
public interface AccountService {

    /** 扣减余额（被 order-service 通过 Feign 调用，Seata AT 参与方；amount 单位：分） */
    void deductBalance(Long userId, Long amount);

    /** 查询余额 */
    AccountBalance getBalance(Long userId);
}
