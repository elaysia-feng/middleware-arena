package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实验模板响应对象（后端输出，纯 DTO）。
 * <p>
 * 只含模板元数据；版本内容（filesJson / runParamsJson）不落在模板上。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TemplateResponse {

    private Long id;

    private String name;

    private String description;

    /** 中间件类型：redis / rabbitmq / seata / elasticsearch / sentinel / hikari 等 */
    private String middlewareType;

    /** 实验场景编码：ORDER_CACHE / RATE_LIMIT 等 */
    private String scenario;

    /** 标签（逗号分隔） */
    private String tags;

    /** 模板状态：DRAFT / ENABLED / DISABLED */
    private String status;

    /** 创建人用户 ID */
    private Long userId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 最新版本 ID（前端进入编辑器后定位版本快照） */
    private Long latestVersionId;

    /** 最新版本号 */
    private Long latestVersionNo;
}
