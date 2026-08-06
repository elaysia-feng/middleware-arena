package com.mware.account.biz;

/**
 * 账户业务接口。
 * <p>
 * TODO[Seata AT 参与方]：
 *   - 扣减余额：被 order-service 通过 Feign 调用
 *   - Seata AT 模式下，undo_log 自动记录回滚日志
 *   - 扣减前校验余额充足（余额不足抛异常触发 Seata 回滚）
 */
public interface AccountService {

}
