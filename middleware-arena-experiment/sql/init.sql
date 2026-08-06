-- middleware-arena-experiment 初始化 SQL（占位，待业务接入后补充完整 DDL）
-- TODO：根据 ExperimentTask 实体字段完善建表语句，补充 experiment_template / experiment_version 等关联表
CREATE TABLE IF NOT EXISTS experiment_task
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT       NOT NULL COMMENT '实验版本快照 ID',
    status     VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '任务状态：pending/queued/running/success/failed/cancelled',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_version (version_id),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='实验任务表';
