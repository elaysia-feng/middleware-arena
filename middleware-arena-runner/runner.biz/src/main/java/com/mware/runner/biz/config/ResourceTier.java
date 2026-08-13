package com.mware.runner.biz.config;

/**
 * 资源等级。等级决定单实验可用的 CPU / 内存额度（额度见 {@link com.mware.runner.biz.config.RunnerProperties.Tiers}）。
 * <p>
 * 当前两级（对齐方案）：
 * <ul>
 *   <li>{@link #FREE}：普通用户任务；</li>
 *   <li>{@link #VIP}：享有预留资源的会员任务。</li>
 * </ul>
 * 未知等级一律按 FREE 兜底，避免脏数据绕过资源限制。
 */
public enum ResourceTier {

    FREE,
    VIP;

    /** 由消息里的 tier 字符串解析（null / 未知 → FREE 兜底） */
    public static ResourceTier from(String tier) {
        if (tier != null) {
            try {
                return ResourceTier.valueOf(tier.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 未登记的等级 → FREE
            }
        }
        return FREE;
    }
}
