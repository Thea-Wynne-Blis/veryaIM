-- VERYAL V6：文件元数据。
-- 文件本体存放在本地磁盘 / MinIO / 阿里云 OSS，MySQL 只保存引用与校验信息。

CREATE TABLE IF NOT EXISTS im_file
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    uploader_id   BIGINT       NOT NULL,
    object_key    VARCHAR(512) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(128) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    storage_type  VARCHAR(16)  NOT NULL,
    status        TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL,
    deleted_at    DATETIME(3)  NULL,

    UNIQUE KEY uk_file_object_key (object_key),
    KEY idx_file_tenant_uploader (tenant_id, uploader_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
