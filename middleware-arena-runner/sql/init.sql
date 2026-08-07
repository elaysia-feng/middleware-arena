-- middleware-arena-runner 初始化 SQL
-- 执行：mysql -u root -p < init.sql（接入 MySQL 时建表）
-- 字段与 RunnerTask 实体对齐

CREATE TABLE IF NOT EXISTS `runner_task`
(
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    task_id         VARCHAR(64)  NOT NULL COMMENT '上游实验任务 ID（experiment_task.id），唯一',
    middleware_type VARCHAR(64)  NULL COMMENT '中间件类型：redis/rabbitmq/nginx/kafka/envoy 等',
    config          TEXT         NULL COMMENT '运行配置（JSON）',
    k6_script       TEXT         NULL COMMENT 'k6 压测脚本（内容或路径）',
    status          VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '阶段状态：pending/building/running/benchmarking/collecting/success/failed/cancelled',
    metrics         TEXT         NULL COMMENT '指标结果（JSON：CPU/内存/延迟/QPS）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Runner 压测任务表';
