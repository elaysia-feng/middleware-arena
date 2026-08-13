package com.mware.runner.biz.config;

/**
 * Runner Redis 键名契约。
 *
 * <p>全团队统一在此定义 Redis 键名，T4（任务实例登记）与 T5（per-instance 定向取消）
 * 都必须使用本类的常量/方法拼接键，禁止在业务代码里手写字符串前缀，
 * 避免键名漂移导致跨服务读写不一致。
 */
public final class RunnerRedisKeys {

    private RunnerRedisKeys() {
        // 工具类，禁止实例化
    }

    /** 任务实例前缀：runner:task:instance:{taskId} */
    public static final String TASK_INSTANCE_PREFIX = "runner:task:instance:";

    /** Runner 全局资源占用量 */
    public static final String RESOURCE_USAGE = "runner:resource:usage";

    /** 任务资源预留前缀 */
    public static final String RESOURCE_RESERVATION_PREFIX = "runner:resource:reservation:";

    /** VIP / FREE 各自的 FIFO 等待队列（ZSet，score=入队时间）。 */
    public static final String RESOURCE_WAITING_VIP = "runner:resource:waiting:vip";
    public static final String RESOURCE_WAITING_FREE = "runner:resource:waiting:free";

    /**
     * 拼接任务实例 Redis 键。
     *
     * @param taskId 任务 ID（若为 Long，请直接使用 {@link #instanceKey(long)} 重载）
     * @return 例如 taskId=10086 → "runner:task:instance:10086"
     */
    public static String instanceKey(String taskId) {
        return TASK_INSTANCE_PREFIX + taskId;
    }

    /** {@link #instanceKey(String)} 的 Long 重载，调用处无需手动转字符串。 */
    public static String instanceKey(long taskId) {
        return instanceKey(Long.toString(taskId));
    }

    public static String resourceReservationKey(long taskId) {
        return RESOURCE_RESERVATION_PREFIX + taskId;
    }
}
