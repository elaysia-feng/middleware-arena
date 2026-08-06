package com.mware.storage.biz;

/**
 * 库存业务接口。
 * <p>
 * TODO[Seata AT 参与方]：
 *   - 扣减库存：被 order-service 通过 Feign 调用
 *   - Seata AT 模式下，undo_log 自动记录回滚日志
 *   - 扣减前校验库存余额
 */
public interface StorageService {

}
