package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个文件的版本差异（后端输出，纯 DTO）。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FileDiff {

    /** 文件相对路径 */
    private String path;

    /** 变化类型：ADDED / MODIFIED / DELETED / UNCHANGED */
    private String changeType;
}
