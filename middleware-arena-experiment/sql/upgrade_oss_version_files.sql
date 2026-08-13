-- 1. 保留 files_json 兼容历史版本，新版本正文改存 OSS。
ALTER TABLE experiment_version
    MODIFY COLUMN files_json MEDIUMTEXT NULL COMMENT '兼容历史数据：改造前的完整文件快照，新版本不再写入',
    ADD COLUMN files_object_key VARCHAR(512) NULL COMMENT 'OSS 对象 Key' AFTER files_json,
    ADD COLUMN files_sha256 CHAR(64) NULL COMMENT 'OSS 对象压缩字节的 SHA-256' AFTER files_object_key,
    ADD COLUMN files_size BIGINT NULL COMMENT 'OSS 对象压缩后的字节数' AFTER files_sha256;

