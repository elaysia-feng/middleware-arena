-- 已存在数据库的 VIP 队列字段升级脚本（MySQL 8.0.29+ 支持 IF NOT EXISTS）。
ALTER TABLE experiment_task
    ADD COLUMN IF NOT EXISTS tier_snapshot VARCHAR(16) NOT NULL DEFAULT 'FREE' COMMENT '本次入队会员等级快照',
    ADD COLUMN IF NOT EXISTS dispatch_id VARCHAR(36) NULL COMMENT '本次投递唯一标识',
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64) NULL COMMENT '机器可识别错误码';
