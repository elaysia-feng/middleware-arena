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
 * 实验版本快照实体：保存实验配置版本，支持回滚。
 * <p>
 * 字段对齐 sql/init.sql 的 experiment_version 表；
 * configSnapshot 存该版本完整配置快照（JSON），对应 editor.html 的"保存版本"。
 */
@Data
@TableName("experiment_version")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    /** 递增版本号 */
    private Integer versionNo;

    /** 该版本的配置快照（JSON） */
    private String configSnapshot;

    private LocalDateTime createdAt;
}
