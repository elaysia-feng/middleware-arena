package com.mware.experiment.mq.producer;

/**
 * Runner 任务消息投递失败补偿钩子。
 *
 * <p>mq 模块只管投递、不碰数据库；confirm=false（Broker 未持久化）或
 * mandatory return（不可路由）时由生产者回调本接口，落库补偿由
 * experiment.biz 的实现完成（把任务置为 FAILED / 告警），保持模块边界。</p>
 *
 * <p>实现方必须幂等：confirm 回调与 send() 同步失败路径可能对同一任务各触发一次。</p>
 */
public interface RunnerDispatchCompensator {

    /**
     * 任务消息投递失败后的补偿。
     *
     * @param taskId      experiment_task.id（correlationData 携带）
     * @param dispatchType 消息类型（CREATE / CANCEL），区分补偿语义
     * @param reason      失败原因（confirm cause / return 描述）
     */
    void onDispatchFailed(Long taskId, String dispatchType, String reason);
}
