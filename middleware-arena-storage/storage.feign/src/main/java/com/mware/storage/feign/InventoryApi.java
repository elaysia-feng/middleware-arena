package com.mware.storage.feign;

/**
 * 库存 Feign 接口定义（占位）。
 * <p>
 * storage 是被调方，feign 模块仅保留目录结构。
 * 实际 Feign 接口由调用方（order-service）的 order.feign 模块定义。
 * <p>
 * TODO[Seata AT]：如需要提供 SDK 给其他服务使用，可在此定义 DTO + 接口契约。
 */
public interface InventoryApi {

    // TODO[Seata AT]：定义扣库存接口契约
}
