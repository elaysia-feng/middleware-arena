package com.mware.experiment.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 创建实验模板请求对象（前端输入，纯 DTO）。
 * <p>
 * - 必传：name / middlewareType / scenario / files（files 决定模板能否运行）
 * - 选传：description / tags / status / runParams（runParams 与 files 一起生成版本 V1）
 * - 后端生成：id / userId / createdAt / updatedAt / latestVersionId / latestVersionNo
 * <p>
 * 更新请用 {@link UpdateTemplateRequest}；两者分开避免 create 的必填校验误伤 update。
 * 不含 id / userId：模板 id 走路径变量，userId 由服务端从
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

    /** 标签（逗号分隔，可选） */
    private String tags;

    /** 模板状态（可选，默认 ENABLED）：DRAFT / ENABLED / DISABLED */
    private String status;

    /** 压测 / 运行参数（可选）：{"concurrencyLadder":[100,300,500],"duration":"30s","timeout":"5m","heap":"1GB"} */
    private Map<String, Object> runParams;

    /** 初始代码文件（必传，创建时生成版本 V1；editable 白名单内嵌每个文件） */
    private List<TemplateFileRequest> files;
}
