package com.mware.experiment.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 更新实验模板请求对象（前端输入，纯 DTO）。
 * <p>
 * 与 {@link CreateTemplateRequest} 分开，避免 create 的必填校验误伤 update：
 * 更新为部分更新（PATCH 语义），所有字段均可选，未传（null）的字段保持原值；
 * 仅当携带 files 时才会生成新版本并推进 latestVersionId。
 * <p>
 * 不含 id / userId：模板 id 走路径变量，userId 由服务端从
 * {@link com.mware.common.web.UserContext} 获取，不信任客户端。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UpdateTemplateRequest {

    /** 模板名称（可选） */
    private String name;

    /** 中间件类型：REDIS / RABBITMQ / SEATA / ELASTICSEARCH 等（可选） */
    private String middlewareType;

    /** 实验场景：ORDER_CACHE / RATE_LIMIT 等（可选） */
    private String scenario;

    /** 模板描述（可选） */
    private String description;

    /** 标签（逗号分隔，可选） */
    private String tags;

    /** 模板状态（可选）：DRAFT / ENABLED / DISABLED */
    private String status;

    /** 压测 / 运行参数（可选）：{"concurrencyLadder":[100,300,500],"duration":"30s","timeout":"5m","heap":"1GB"} */
    private Map<String, Object> runParams;

    /** 代码文件（可选，携带时生成新版本；editable 白名单内嵌每个文件） */
    private List<TemplateFileRequest> files;
}
