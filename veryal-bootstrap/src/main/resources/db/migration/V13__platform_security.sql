-- VERYAL V13：平台管理员登录锁定与 Refresh Token。

ALTER TABLE im_platform_admin
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until DATETIME(3) NULL;

CREATE TABLE IF NOT EXISTS im_platform_refresh_token
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    admin_id   BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked    TINYINT     NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,

    UNIQUE KEY uk_platform_refresh_hash (token_hash),
    KEY idx_platform_refresh_admin (admin_id, expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
