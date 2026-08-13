-- ============================================
-- auth 服务建表脚本
-- 密码由 AuthServiceImpl 用 BCrypt 加密后写入（生成 60 字符哈希），此处只存密文
-- ============================================
CREATE TABLE IF NOT EXISTS `user` (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 密文（60 字符），由业务代码加密后写入',
    nickname    VARCHAR(64)  DEFAULT NULL,
    tier        VARCHAR(16)  NOT NULL DEFAULT 'FREE' COMMENT '存储等级：FREE / VIP',
    vip_expire_at DATETIME   DEFAULT NULL COMMENT 'VIP 到期时间，业务访问时实时判断',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
