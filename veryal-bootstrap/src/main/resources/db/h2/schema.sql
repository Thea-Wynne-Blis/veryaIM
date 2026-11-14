-- VERYAL H2 schema（MODE=MySQL），仅用于本地开发与集成测试。
-- 生产环境请使用 Flyway + MySQL 迁移脚本（db/migration）。

CREATE TABLE IF NOT EXISTS im_user
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    nickname      VARCHAR(128),
    avatar        VARCHAR(512),
    status        TINYINT      NOT NULL DEFAULT 1,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP(3) NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_tenant_username ON im_user (tenant_id, username);

CREATE TABLE IF NOT EXISTS im_device
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    device_id     VARCHAR(64)  NOT NULL,
    platform      VARCHAR(32)  NOT NULL,
    device_name   VARCHAR(128),
    last_login_at TIMESTAMP(3),
    created_at    TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_id ON im_device (device_id);
CREATE INDEX IF NOT EXISTS idx_device_user_id ON im_device (user_id);

CREATE TABLE IF NOT EXISTS im_conversation
(
    id                VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id         BIGINT      NOT NULL,
    conversation_type TINYINT     NOT NULL,
    created_at        TIMESTAMP(3) NOT NULL,
    updated_at        TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_conversation_tenant_type ON im_conversation (tenant_id, conversation_type);

CREATE TABLE IF NOT EXISTS im_conversation_member
(
    id              BIGINT      NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    user_id         BIGINT      NOT NULL,
    last_read_seq   BIGINT      NOT NULL DEFAULT 0,
    last_sync_seq   BIGINT      NOT NULL DEFAULT 0,
    joined_at       TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_user ON im_conversation_member (conversation_id, user_id);
CREATE INDEX IF NOT EXISTS idx_member_user_id ON im_conversation_member (user_id);

CREATE TABLE IF NOT EXISTS im_conversation_sequence
(
    conversation_id VARCHAR(64) NOT NULL PRIMARY KEY,
    last_seq        BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS im_message
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    conversation_id VARCHAR(64)  NOT NULL,
    sender_id       BIGINT       NOT NULL,
    client_msg_id   VARCHAR(64)  NOT NULL,
    seq             BIGINT       NOT NULL,
    msg_type        SMALLINT     NOT NULL,
    content         BLOB         NOT NULL,
    created_at      TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sender_client_msg ON im_message (tenant_id, sender_id, client_msg_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_seq ON im_message (tenant_id, conversation_id, seq);
CREATE INDEX IF NOT EXISTS idx_conversation_time ON im_message (conversation_id, created_at);
