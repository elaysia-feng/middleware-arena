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
 * 实验模板实体：定义实验步骤 / 参数 / 中间件组合。
 * <p>
 * 字段对齐 sql/init.sql 的 experiment_template 表；
 * configJson 存代码文件树 + 运行参数（JSON），对应前端 demo 的 editor.html。
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

    /** 实验配置（JSON）：代码文件树 + 运行参数 */
    private String configJson;

    private LocalDateTime createdAt;
}
