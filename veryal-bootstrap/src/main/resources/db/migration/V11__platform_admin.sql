-- VERYAL V11：平台管理员账号。

CREATE TABLE IF NOT EXISTS im_platform_admin
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    display_name  VARCHAR(64)  NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    status        TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL,
    last_login_at DATETIME(3)  NULL,

    UNIQUE KEY uk_platform_admin_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
