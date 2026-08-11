package com.mware.runner.biz.execution.impl;

import com.mware.runner.biz.execution.RunningTaskManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * 本地运行任务登记表实现：ConcurrentHashMap&lt;taskId, Future&gt;。
 */
@Component
public class RunningTaskManagerImpl implements RunningTaskManager {

    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    @Override
    public void register(String taskId, Future<?> future) {
        runningTasks.put(taskId, future);
    }

    @Override
    public Future<?> get(String taskId) {
        return runningTasks.get(taskId);
    }

    @Override
    public void remove(String taskId) {
        runningTasks.remove(taskId);
    }

    @Override
    public boolean cancel(String taskId) {
        Future<?> future = runningTasks.get(taskId);

        if (future == null) {
            return false;
        }

        boolean cancelled = future.cancel(true);
        // cancel 后立即移除登记：任务线程的 finally 也会 remove（幂等）；
        // 若任务线程 hang 住永不触及 finally，这里保证登记表不残留。
        runningTasks.remove(taskId);
        return cancelled;
    }
}
