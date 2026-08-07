package com.mware.runner.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交一次运行任务的请求。
 * <p>
 * 字段与 {@link com.mware.runner.biz.RunnerService} 流水线各环节所需的描述信息对齐，
 * 后续接入 MQ / docker / k6 时按需补全，避免过度设计。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunRequest {

    /** 上游实验任务 ID */
    private String taskId;

    /** 中间件类型（Nginx / Redis / Kafka / Envoy 等） */
    private String middlewareType;

    /** 中间件版本 */
    private String version;

    /** 运行配置（JSON） */
    private String config;

    /** k6 压测脚本（脚本内容或路径） */
    private String k6Script;
}
