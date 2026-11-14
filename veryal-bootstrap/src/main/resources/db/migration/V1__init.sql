-- VERYAL V1 初始化迁移（MySQL 8.0.30）
-- 约定：UTC 时间、utf8mb4、InnoDB、DATETIME(3)。
-- 注意：im_user.password_hash 为认证所需扩展字段（文档核心表结构未列出）。

CREATE TABLE IF NOT EXISTS im_user
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    nickname      VARCHAR(128),
    avatar        VARCHAR(512),
    status        TINYINT      NOT NULL DEFAULT 1,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,

    UNIQUE KEY uk_user_tenant_username (tenant_id, username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_device
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    device_id     VARCHAR(64)  NOT NULL,
    platform      VARCHAR(32)  NOT NULL,
    device_name   VARCHAR(128),
    last_login_at DATETIME(3),
    created_at    DATETIME(3)  NOT NULL,

    UNIQUE KEY uk_device_id (device_id),
    KEY idx_device_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_conversation
(
    id                VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id         BIGINT      NOT NULL,
    conversation_type TINYINT     NOT NULL,
    created_at        DATETIME(3) NOT NULL,
    updated_at        DATETIME(3) NOT NULL,

    KEY idx_conversation_tenant_type (tenant_id, conversation_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_conversation_member
(
    id              BIGINT      NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    user_id         BIGINT      NOT NULL,
    last_read_seq   BIGINT      NOT NULL DEFAULT 0,
    last_sync_seq   BIGINT      NOT NULL DEFAULT 0,
    joined_at       DATETIME(3) NOT NULL,

    UNIQUE KEY uk_conversation_user (conversation_id, user_id),
    KEY idx_member_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_conversation_sequence
(
    conversation_id VARCHAR(64) NOT NULL PRIMARY KEY,
    last_seq        BIGINT      NOT NULL DEFAULT 0
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_message
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    conversation_id VARCHAR(64)  NOT NULL,
    sender_id       BIGINT       NOT NULL,
    client_msg_id   VARCHAR(64)  NOT NULL,
    seq             BIGINT       NOT NULL,
    msg_type        SMALLINT     NOT NULL,
    content         MEDIUMBLOB   NOT NULL,
    created_at      DATETIME(3)  NOT NULL,

    UNIQUE KEY uk_sender_client_msg (tenant_id, sender_id, client_msg_id),
    UNIQUE KEY uk_conversation_seq (tenant_id, conversation_id, seq),
    KEY idx_conversation_time (conversation_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
