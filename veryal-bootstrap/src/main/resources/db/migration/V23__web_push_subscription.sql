-- VERYAL V23：浏览器 Web Push 订阅。

CREATE TABLE IF NOT EXISTS im_web_push_subscription
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    endpoint      TEXT         NOT NULL,
    endpoint_hash CHAR(64)     NOT NULL,
    p256dh        VARCHAR(128) NOT NULL,
    auth          VARCHAR(64)  NOT NULL,
    user_agent    VARCHAR(255) NULL,
    enabled       TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,

    UNIQUE KEY uk_web_push_endpoint_hash (endpoint_hash),
    KEY idx_web_push_user (user_id, enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
