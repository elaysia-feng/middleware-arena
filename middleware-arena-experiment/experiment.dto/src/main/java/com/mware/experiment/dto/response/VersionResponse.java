package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实验版本快照响应对象（后端输出，纯 DTO）。
 * <p>
 * filesJson / runParamsJson 为版本内容，仅属主可见（非属主返回 null）；
 * versionNo / changeSummary / createdBy 为版本元数据，公开。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class VersionResponse {

    private Long id;

    private Long templateId;

    /** 递增版本号 */
    private Long versionNo;

    /** 完整代码文件快照（JSON 数组） */
    private String filesJson;

    /** 压测 / 运行参数（JSON） */
    private String runParamsJson;

    /** 修改说明 */
    private String changeSummary;

    /** 创建人用户 ID */
    private Long createdBy;

    private LocalDateTime createdAt;
}
