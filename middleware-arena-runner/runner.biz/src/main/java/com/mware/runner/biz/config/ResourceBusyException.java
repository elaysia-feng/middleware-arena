package com.mware.runner.biz.config;

/**
 * 平台资源不足时抛出：表示任务未获得容器资源，绝不能创建容器。
 * <p>
 * {@code execute()} 捕获本异常后走"重新排队"（保留消息在队列等资源），
 * 与普通业务异常（重试 3 次后进 DLQ）区分开，避免资源打满时任务被误判为失败。
 */
public class ResourceBusyException extends RuntimeException {

    public ResourceBusyException(String message) {
        super(message);
    }

    public ResourceBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
