-- Agent 分析持久化骨架。
-- 1. experiment_analysis 保存一次 Agent 分析任务与最终诊断结果。
-- 2. experiment_patch 保存 Agent 生成、用户确认、最终应用的新版本关系。
-- 3. MQ 只传 analysis_id/task_id/version_id 等轻量字段；完整分析结果最终落数据库。

CREATE TABLE IF NOT EXISTS experiment_analysis
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '分析 ID，同时作为 MQ analysisId',
    task_id              BIGINT        NOT NULL COMMENT '来源实验任务 experiment_task.id',
    user_id              BIGINT        NOT NULL COMMENT '任务所属用户，用于审计/限额',
    version_id           BIGINT        NOT NULL COMMENT '被分析代码版本 experiment_version.id',
    baseline_task_id     BIGINT        NULL COMMENT '对比基线任务；为空时 Agent 可按策略寻找',
    middleware_type      VARCHAR(64)   NOT NULL COMMENT 'redis/rabbitmq/seata/elasticsearch 等',
    analysis_type        VARCHAR(32)   NOT NULL DEFAULT 'PERFORMANCE_DIAGNOSIS' COMMENT '分析类型',
    trigger_type         VARCHAR(16)   NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO/MANUAL/RETRY',
    status               VARCHAR(16)   NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/QUEUED/ANALYZING/SUCCESS/FAILED',
    current_stage        VARCHAR(32)   NULL COMMENT 'LOAD_CONTEXT/METRICS/CODE/HYPOTHESIS/JUDGE/PATCH/REPORT',
    progress             INT           NOT NULL DEFAULT 0 COMMENT '0~100',
    bottleneck_type      VARCHAR(64)   NULL COMMENT '最终瓶颈类型',
    confidence           DOUBLE        NULL COMMENT '0~1 置信度',
    evidence_json        MEDIUMTEXT    NULL COMMENT '证据列表 JSON',
    hypotheses_json      MEDIUMTEXT    NULL COMMENT '候选假设 JSON',
    suggestions_json     MEDIUMTEXT    NULL COMMENT '优化建议 JSON',
    report               MEDIUMTEXT    NULL COMMENT '最终分析报告',
    model_name           VARCHAR(128)  NULL COMMENT '主要 LLM 模型',
    prompt_versions_json TEXT          NULL COMMENT '本次各 Langfuse Prompt 版本',
    langfuse_trace_id    VARCHAR(128)  NULL COMMENT 'Langfuse Trace ID',
    dispatch_id          VARCHAR(36)   NOT NULL COMMENT 'MQ 投递唯一标识，防重复消费',
    error_code           VARCHAR(64)   NULL COMMENT '失败错误码',
    error_message        VARCHAR(1024) NULL COMMENT '失败原因',
    started_at           DATETIME      NULL,
    finished_at          DATETIME      NULL,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_analysis_dispatch (dispatch_id),
    INDEX idx_analysis_task (task_id),
    INDEX idx_analysis_version (version_id),
    INDEX idx_analysis_user (user_id),
    INDEX idx_analysis_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 实验分析表';

CREATE TABLE IF NOT EXISTS experiment_patch
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'Patch ID',
    analysis_id        BIGINT       NOT NULL COMMENT '来源 experiment_analysis.id',
    source_version_id  BIGINT       NOT NULL COMMENT 'Patch 基于哪个版本生成',
    status             VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/ACCEPTED/REJECTED/APPLIED',
    summary            VARCHAR(512) NULL COMMENT 'Patch 修改摘要',
    files_patch_json   MEDIUMTEXT   NOT NULL COMMENT '多文件 patch/diff JSON',
    validation_json    TEXT         NULL COMMENT '静态检查/构建验证结果',
    applied_version_id BIGINT       NULL COMMENT '用户接受后创建的新 experiment_version.id',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patch_analysis (analysis_id),
    INDEX idx_patch_source_version (source_version_id),
    INDEX idx_patch_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 候选代码 Patch 表';
