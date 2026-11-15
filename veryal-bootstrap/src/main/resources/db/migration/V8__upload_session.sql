-- VERYAL V8：分片上传会话。

CREATE TABLE IF NOT EXISTS im_upload_session
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    uploader_id     BIGINT       NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(128) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    chunk_size      INT          NOT NULL,
    total_chunks    INT          NOT NULL,
    conversation_id VARCHAR(64)  NULL,
    status          TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME(3)  NOT NULL,
    file_id         BIGINT       NULL,

    KEY idx_upload_tenant_uploader (tenant_id, uploader_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
