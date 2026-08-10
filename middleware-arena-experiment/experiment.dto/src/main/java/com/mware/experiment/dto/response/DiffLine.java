package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单文件的行级差异（后端输出，纯 DTO）。
 * <p>
 * 用于 diffVersion 的 MODIFIED / ADDED / DELETED 文件明细：
 * {@code type=ADD} 表示目标版本新增的行（{@code newLineNo} 为 to 版本行号），
 * {@code type=REMOVE} 表示基准版本中被删除的行（{@code oldLineNo} 为 from 版本行号）。
 * 前端可直接渲染成 git 风格（红 − / 绿 +）。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DiffLine {

    /** 行变化类型：ADD（新增）/ REMOVE（删除） */
    private String type;

    /** 基准版本行号（REMOVE 有值；ADD 为 null） */
    private Integer oldLineNo;

    /** 目标版本行号（ADD 有值；REMOVE 为 null） */
    private Integer newLineNo;

    /** 该行内容 */
    private String content;
}
