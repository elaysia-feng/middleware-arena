package com.mware.runner.biz.execution;

import java.util.concurrent.Future;

/**
 * 本地运行任务登记表（taskId → Future），取消任务时使用。
 * <p>
 * 取消语义：{@link #cancel(String)} 中断持有该任务 Future 的线程并立即移除登记；
 * 任务线程的 finally 也会 {@link #remove(String)}（幂等），双保险保证登记表不残留。
 */
public interface RunningTaskManager {

    void register(String taskId, Future<?> future);

    Future<?> get(String taskId);

    void remove(String taskId);

    boolean cancel(String taskId);
}
