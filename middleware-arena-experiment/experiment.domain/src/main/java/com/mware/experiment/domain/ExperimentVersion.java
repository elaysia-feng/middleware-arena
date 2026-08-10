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
 * 版本内容只存在这里（模板只存元数据 + latestVersionId）：
 * filesJson 存完整代码文件快照（editable 白名单内嵌每个文件），
 * runParamsJson 存压测 / 运行参数。V1/V2 各一行，diff 即对比两版 filesJson。
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

    /**
     * 完整代码文件快照（JSON 数组）：
     * [{"path":"OrderService.java","language":"java","content":"...","editable":true}]
     */
    private String filesJson;

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
