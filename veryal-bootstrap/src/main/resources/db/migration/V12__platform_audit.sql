-- VERYAL V12：平台管理员操作审计。

CREATE TABLE IF NOT EXISTS im_platform_audit_log
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    admin_id    BIGINT       NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(128) NOT NULL,
    detail      VARCHAR(512) NULL,
    created_at  DATETIME(3)  NOT NULL,

    KEY idx_platform_audit_admin (admin_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
