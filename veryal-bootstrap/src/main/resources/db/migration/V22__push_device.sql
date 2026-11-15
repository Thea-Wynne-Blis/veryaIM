-- VERYAL V22：推送设备 token。

CREATE TABLE IF NOT EXISTS im_push_device
(
    id         BIGINT       NOT NULL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    device_id  VARCHAR(64)  NOT NULL,
    platform   VARCHAR(16)  NOT NULL,
    token      VARCHAR(512) NOT NULL,
    enabled    TINYINT      NOT NULL DEFAULT 1,
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,

    UNIQUE KEY uk_push_user_device (user_id, device_id),
    KEY idx_push_user_enabled (user_id, enabled),
    KEY idx_push_platform (platform, enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
