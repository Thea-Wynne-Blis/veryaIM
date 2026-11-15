-- VERYAL V10：好友申请、好友关系、黑名单。

CREATE TABLE IF NOT EXISTS im_friend_request
(
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    from_user_id BIGINT       NOT NULL,
    to_user_id   BIGINT       NOT NULL,
    message      VARCHAR(255) NULL,
    status       TINYINT      NOT NULL DEFAULT 0,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL,

    UNIQUE KEY uk_friend_request_direction (tenant_id, from_user_id, to_user_id),
    KEY idx_friend_request_to (tenant_id, to_user_id, status, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_friend
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    tenant_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    friend_id  BIGINT      NOT NULL,
    remark     VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,

    UNIQUE KEY uk_friend_pair (tenant_id, user_id, friend_id),
    KEY idx_friend_user (tenant_id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_blacklist
(
    id              BIGINT      NOT NULL PRIMARY KEY,
    tenant_id       BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    blocked_user_id BIGINT      NOT NULL,
    created_at      DATETIME(3) NOT NULL,

    UNIQUE KEY uk_blacklist_pair (tenant_id, user_id, blocked_user_id),
    KEY idx_blacklist_user (tenant_id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
