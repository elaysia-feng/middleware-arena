package com.mware.experiment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实验模板实体：只描述"这是什么实验"（纯元数据）。
 * <p>
 * 版本内容（代码文件快照 / 运行参数）不落在模板上，只存在 {@link ExperimentVersion}；
 * 模板仅保存 {@code latestVersionId} 指向最新版本，避免重复保存"最新完整快照"。
 */
@Data
@TableName("experiment_template")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建人用户 ID */
    private Long userId;

    private String name;

    private String description;

    /** 中间件类型：redis / rabbitmq / seata / elasticsearch / sentinel / hikari 等 */
    private String middlewareType;

    /** 实验场景编码：ORDER_CACHE / RATE_LIMIT 等 */
    private String scenario;

    /** 标签（逗号分隔），对应场景卡片 tag：如 "缓存命中率,热点 Key" */
    private String tags;

    /** 模板状态：DRAFT / ENABLED / DISABLED，控制场景卡片是否可见 */
    private String status;

    /** 当前最新版本 ID（experiment_version.id），新建版本时更新 */
    private Long latestVersionId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
