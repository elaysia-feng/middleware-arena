package com.mware.experiment.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板代码文件（前端输入，纯 DTO）。
 * <p>
 * 随 {@link CreateTemplateRequest#getFiles()} 传入，落版本快照 config_snapshot。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TemplateFileRequest {

    /** 文件相对路径：src/main/java/OrderService.java */
    private String path;

    /** 文件内容 */
    private String content;

    /** 语言：java / yaml / nginx / go 等 */
    private String language;
}
