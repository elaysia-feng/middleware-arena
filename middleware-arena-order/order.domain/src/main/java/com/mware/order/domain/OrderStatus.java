package com.mware.order.domain;

import lombok.Getter;

/**
 * 订单状态枚举。
 * <p>
 * 状态流转（配合 Seata 分布式事务链路）：
 * <pre>
 *   CREATE（已创建）─── 扣库存 + 扣余额成功 ──▶ PAID（已支付）
 *        │
 *        └─── 用户取消 / 事务回滚 ──▶ CANCEL（已取消）
 * </pre>
 * 下单时写入 CREATE；{@code storageService.deductStock} 与 {@code accountService.deductBalance}
 *
 * Seata AT 全局回滚，或标记为 CANCEL。
 */
@Getter
public enum OrderStatus {

    /** 已创建：下单成功，尚未完成库存 / 余额扣减 */
    CREATE("CREATE", "已创建"),

    /** 已支付：库存扣减 + 余额扣减均成功，订单完成 */
    PAID("PAID", "已支付"),

    /** 已取消：用户主动取消，或扣减失败事务回滚 */
    CANCEL("CANCEL", "已取消");

    /** 数据库存储值（与列定义一致，见 sql/init.sql） */
    private final String status;

    /** 中文描述（便于日志 / 响应展示） */
    private final String desc;

    OrderStatus(String status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    /**
     * 按数据库存储值反查枚举；未知值抛 {@link IllegalArgumentException}。
     *
     * @param status 数据库中的状态字符串（如 "PAID"）
     * @return 对应的枚举常量
     */
    public static OrderStatus fromStatus(String status) {
        for (OrderStatus s : values()) {
            if (s.status.equals(status)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知订单状态: " + status);
    }
}
