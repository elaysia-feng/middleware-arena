package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实验模板响应对象（后端输出，纯 DTO）。
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

    /** 实验配置（JSON）：代码文件树 + 运行参数 */
    private String configJson;

    /** 创建人用户 ID */
    private Long userId;

    private LocalDateTime createdAt;
}
