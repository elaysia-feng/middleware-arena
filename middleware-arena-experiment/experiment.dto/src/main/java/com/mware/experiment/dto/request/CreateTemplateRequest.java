package com.mware.experiment.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 创建 / 更新实验模板请求对象（前端输入，纯 DTO）。
 * <p>
 * - 必传：name / middlewareType / scenario / files（files 决定模板能否运行）
 * - 选传：description / config（运行参数，如 concurrency / duration）
 * - 后端生成：id / userId(createdBy) / createdAt / versionId / versionNo / status
 * <p>
 * 不含 id / userId：更新时模板 id 走路径变量，userId 由服务端从
 * {@link com.mware.common.web.UserContext} 获取，不信任客户端。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CreateTemplateRequest {

    /** 模板名称 */
    private String name;

    /** 中间件类型：REDIS / RABBITMQ / SEATA / ELASTICSEARCH 等 */
    private String middlewareType;

    /** 实验场景：ORDER_CACHE / RATE_LIMIT 等 */
    private String scenario;

    /** 模板描述（可选） */
    private String description;

    /** 运行参数（可选）：concurrency / duration 等 */
    private Map<String, Object> config;

    /** 初始代码文件（必传，创建时生成版本 V1 快照） */
    private List<TemplateFileRequest> files;
}
