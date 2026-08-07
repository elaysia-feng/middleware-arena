package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实验版本快照响应对象（后端输出，纯 DTO）。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class VersionResponse {

    private Long id;

    private Long templateId;

    /** 递增版本号 */
    private Integer versionNo;

    /** 该版本的配置快照（JSON） */
    private String configSnapshot;

    private LocalDateTime createdAt;
}
