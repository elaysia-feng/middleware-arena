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
 * 实验版本实体：保存"这个实验的 V3 到底长什么样"。
 * <p>
 * 版本正文存放在 OSS，数据库只保存对象 Key、SHA-256 和大小。
 * filesJson 仅用于兼容改造前的历史数据，新版本不再写入该字段。
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

    /** 递增版本号（同模板内唯一，rollbackVersion 依赖） */
    private Long versionNo;

    /** 兼容改造前的历史 JSON 快照；新版本为 null。 */
    private String filesJson;

    /** OSS 对象 Key，例如 experiments/1/versions/xxx.json.gz。 */
    private String filesObjectKey;

    /** OSS 对象压缩字节的 SHA-256，用于 Runner 下载后校验。 */
    private String filesSha256;

    /** OSS 对象压缩后的字节数。 */
    private Long filesSize;

    /**
     * 压测 /
     * 运行参数（JSON）：{"concurrencyLadder":[100,300,500],"duration":"30s","timeout":"5m","heap":"1GB"}
     */
    private String runParamsJson;

    /** 修改说明 */
    private String changeSummary;

    /** 创建人用户 ID */
    private Long createdBy;

    private LocalDateTime createdAt;
}
