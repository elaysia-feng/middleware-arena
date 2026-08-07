package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 两个实验版本对比结果（后端输出，纯 DTO）。
 * <p>
 * 用于 diffVersion：按文件维度给出每个路径的新增 / 修改 / 删除 / 未变化。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class VersionDiffResponse {

    /** 对比基准版本 ID */
    private Long fromVersionId;

    /** 对比目标版本 ID */
    private Long toVersionId;

    private Integer fromVersionNo;

    private Integer toVersionNo;

    /** 文件级差异列表 */
    private List<FileDiff> fileDiffs;
}
