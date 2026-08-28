package com.mware.experiment.biz.log;

import com.mware.experiment.dto.response.TaskLogResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保存当前 experiment-service 进程内的任务日志。
 * <p>
 * 日志只用于运行期调试，不把整台机器的 Runner 日志暴露给前端；服务重启后历史实时日志会清空。
 */
@Component
public class TaskLogStore {

    private static final int MAX_LOGS_PER_TASK = 200;

    private final Map<Long, Deque<TaskLogResponse>> logsByTask = new ConcurrentHashMap<>();

    public void clear(Long taskId) {
        if (taskId != null) {
            logsByTask.remove(taskId);
        }
    }

    public void append(Long taskId, String level, String stage, String message, Long occurredAtEpochMs) {
        if (taskId == null || message == null || message.isBlank()) {
            return;
        }

        Deque<TaskLogResponse> logs = logsByTask.computeIfAbsent(taskId, ignored -> new ArrayDeque<>());
        TaskLogResponse entry = TaskLogResponse.builder()
                .occurredAtEpochMs(occurredAtEpochMs == null ? System.currentTimeMillis() : occurredAtEpochMs)
                .level(level == null || level.isBlank() ? "INFO" : level)
                .stage(stage)
                .message(limitMessage(message))
                .build();
        synchronized (logs) {
            logs.addLast(entry);
            while (logs.size() > MAX_LOGS_PER_TASK) {
                logs.removeFirst();
            }
        }
    }

    public List<TaskLogResponse> list(Long taskId, int limit) {
        Deque<TaskLogResponse> logs = logsByTask.get(taskId);
        if (logs == null) {
            return List.of();
        }
        synchronized (logs) {
            List<TaskLogResponse> snapshot = new ArrayList<>(logs);
            int fromIndex = Math.max(0, snapshot.size() - limit);
            return List.copyOf(snapshot.subList(fromIndex, snapshot.size()));
        }
    }

    private String limitMessage(String message) {
        int maxLength = 4000;
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }
}
