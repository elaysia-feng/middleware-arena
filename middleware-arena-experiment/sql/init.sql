-- middleware-arena-experiment 初始化 SQL
-- 执行：mysql -u root -p < init.sql（接入 MySQL 时建表）
-- 职责划分：模板=元数据、版本=内容快照、任务=运行状态、结果=压测指标
-- 版本内容（files_json / run_params_json）只存 experiment_version，模板不重复保存最新快照

-- 实验模板表（只存元数据，@TableName = experiment_template）
CREATE TABLE IF NOT EXISTS experiment_template
(
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '模板 ID',
    user_id           BIGINT       NOT NULL COMMENT '创建人用户 ID（由 UserContext 注入，不信任客户端）',
    name              VARCHAR(128) NOT NULL COMMENT '模板名称',
    description       VARCHAR(512) NULL COMMENT '模板描述',
    middleware_type   VARCHAR(64)  NOT NULL COMMENT '中间件类型：redis/rabbitmq/seata/elasticsearch/sentinel/hikari 等',
    scenario          VARCHAR(128) NULL COMMENT '实验场景编码：ORDER_CACHE / RATE_LIMIT 等',
    tags              VARCHAR(512) NULL COMMENT '标签（逗号分隔），对应场景卡片 tag',
    status            VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '模板状态：DRAFT / ENABLED / DISABLED，控制场景卡片是否可见',
    latest_version_id BIGINT       NULL COMMENT '当前最新版本 ID（experiment_version.id），新建版本时更新',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_middleware (middleware_type),
    INDEX idx_scenario (scenario),
    INDEX idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='实验模板表';

-- 实验版本表（版本内容只存在这里，@TableName = experiment_version）
CREATE TABLE IF NOT EXISTS experiment_version
(
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '版本 ID',
    template_id     BIGINT        NOT NULL COMMENT '所属模板 ID（experiment_template.id）',
    version_no      INT           NOT NULL COMMENT '递增版本号（同模板内唯一，rollbackVersion 依赖）',
    files_json      TEXT          NULL COMMENT '完整代码文件快照（JSON 数组，editable 白名单内嵌每个文件）',
    run_params_json TEXT         NULL COMMENT '压测/运行参数（JSON）：concurrencyLadder/duration/timeout/heap 等',
    change_summary  VARCHAR(512)  NULL COMMENT '修改说明',
    created_by      BIGINT        NULL COMMENT '创建人用户 ID',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_template_version (template_id, version_no),
    INDEX idx_template (template_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='实验版本表';

-- 实验任务表（只记运行状态，@TableName = experiment_task；runner 侧不持久化）
CREATE TABLE IF NOT EXISTS experiment_task
(
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '任务 ID',
    user_id       BIGINT       NOT NULL COMMENT '创建人用户 ID',
    version_id    BIGINT       NOT NULL COMMENT '实验版本快照 ID',
    status        VARCHAR(16)  NOT NULL DEFAULT 'QUEUED' COMMENT '任务状态：QUEUED/RUNNING/SUCCESS/FAILED/CANCELLED',
    current_stage VARCHAR(16)  NULL COMMENT '当前阶段：BUILDING/STARTING/BENCHMARKING/COLLECTING/ANALYZING',
    progress      INT          NULL COMMENT '进度 0~100',
    error_message VARCHAR(1024) NULL COMMENT '失败原因（status=FAILED 时有值）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    started_at    DATETIME     NULL COMMENT '开始执行时间',
    finished_at   DATETIME     NULL COMMENT '结束时间（成功/失败/取消）',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (user_id),
    INDEX idx_version (version_id),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='实验任务表';

-- 实验结果表（压测指标结构化，排行榜直接 ORDER BY qps DESC，@TableName = experiment_result）
CREATE TABLE IF NOT EXISTS experiment_result
(
    id             BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '结果 ID',
    task_id        BIGINT   NOT NULL COMMENT '实验任务 ID（experiment_task.id），一任务一结果',
    qps            DOUBLE   NULL COMMENT '吞吐量 QPS',
    p95_ms         BIGINT   NULL COMMENT 'P95 延迟（毫秒）',
    error_rate     DOUBLE   NULL COMMENT '错误率（0~1）',
    avg_cpu        DOUBLE   NULL COMMENT '平均 CPU 使用率（0~1）',
    peak_memory_mb BIGINT   NULL COMMENT '峰值内存（MB）',
    metrics_json   TEXT     NULL COMMENT '原始完整指标（JSON）',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_task_id (task_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='实验结果表';
