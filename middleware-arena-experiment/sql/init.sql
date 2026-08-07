-- middleware-arena-experiment 初始化 SQL
-- 执行：mysql -u root -p < init.sql（接入 MySQL 时建表）
-- 字段与 ExperimentTask / ExperimentTemplate 实体对齐；experiment_version 关联表待业务接入后补充

CREATE TABLE IF NOT EXISTS experiment_task
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务 ID',
    user_id     BIGINT       NOT NULL COMMENT '创建人用户 ID',
    version_id  BIGINT       NOT NULL COMMENT '实验版本快照 ID',
    name        VARCHAR(128) NOT NULL COMMENT '实验任务名称',
    description VARCHAR(512) NULL COMMENT '任务描述',
    status      VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '任务状态：pending/queued/running/success/failed/cancelled',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (user_id),
    INDEX idx_version (version_id),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='实验任务表';

-- 实验模板表（对应 ExperimentTemplate 实体，@TableName = experiment_template）
CREATE TABLE IF NOT EXISTS experiment_template
(
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '模板 ID',
    user_id         BIGINT       NOT NULL COMMENT '创建人用户 ID（由 UserContext 注入，不信任客户端）',
    name            VARCHAR(128) NOT NULL COMMENT '模板名称',
    description     VARCHAR(512) NULL COMMENT '模板描述',
    middleware_type VARCHAR(64)  NOT NULL COMMENT '中间件类型：redis/rabbitmq/seata/elasticsearch/sentinel/hikari 等',
    scenario        VARCHAR(128) NULL COMMENT '实验场景编码：ORDER_CACHE / RATE_LIMIT 等',
    tags            VARCHAR(512) NULL COMMENT '标签（逗号分隔），对应场景卡片 tag',
    config_json     TEXT         NULL COMMENT '实验配置（JSON）：代码文件树 + 运行参数',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_middleware (middleware_type),
    INDEX idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='实验模板表';

-- 实验版本表（对应 ExperimentVersion 实体，@TableName = experiment_version）
CREATE TABLE IF NOT EXISTS experiment_version
(
    id              BIGINT  NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '版本 ID',
    template_id     BIGINT  NOT NULL COMMENT '所属模板 ID（experiment_template.id）',
    version_no      INT     NOT NULL COMMENT '递增版本号（同模板内唯一，rollbackVersion 依赖）',
    config_snapshot TEXT    NULL COMMENT '该版本配置快照（JSON）',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_template_version (template_id, version_no),
    INDEX idx_template (template_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='实验版本表';
