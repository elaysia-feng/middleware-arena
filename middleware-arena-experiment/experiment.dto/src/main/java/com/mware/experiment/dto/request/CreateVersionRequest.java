package com.mware.experiment.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存实验版本请求。
 * <p>
 * 文件正文较大，必须放在 POST 请求体中，不能拼接到 URL 查询参数。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CreateVersionRequest {

    private Long templateId;

    private String filesJson;

    private String runParamsJson;

    private String changeSummary;
}
