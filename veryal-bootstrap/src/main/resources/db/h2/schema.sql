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

CREATE TABLE IF NOT EXISTS im_outbox
(
    id            BIGINT      NOT NULL PRIMARY KEY,
    event_id      VARCHAR(64) NOT NULL,
    aggregate_id  VARCHAR(64) NOT NULL,
    event_type    VARCHAR(64) NOT NULL,
    payload       BLOB        NOT NULL,
    status        TINYINT     NOT NULL DEFAULT 0,
    retry_count   INT         NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP(3) NOT NULL,
    last_error    VARCHAR(512),
    created_at    TIMESTAMP(3) NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL,
    published_at  TIMESTAMP(3)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_outbox_event_id ON im_outbox (event_id);
CREATE INDEX IF NOT EXISTS idx_outbox_status_retry ON im_outbox (status, next_retry_at);

CREATE TABLE IF NOT EXISTS im_group
(
    id         VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL,
    name       VARCHAR(128) NOT NULL,
    owner_id   BIGINT       NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_group_tenant ON im_group (tenant_id);

CREATE TABLE IF NOT EXISTS im_group_member
(
    id        BIGINT       NOT NULL PRIMARY KEY,
    group_id  VARCHAR(64)  NOT NULL,
    user_id   BIGINT       NOT NULL,
    role      TINYINT      NOT NULL DEFAULT 0,
    joined_at TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_group_user ON im_group_member (group_id, user_id);
CREATE INDEX IF NOT EXISTS idx_group_member_user ON im_group_member (user_id);

CREATE TABLE IF NOT EXISTS im_message_delivery
(
    id                 BIGINT       NOT NULL PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL,
    message_id         BIGINT       NOT NULL,
    conversation_id    VARCHAR(64)  NOT NULL,
    user_id            BIGINT       NOT NULL,
    device_id          VARCHAR(64)  NOT NULL,
    delivered_sequence BIGINT       NOT NULL DEFAULT 0,
    delivered_at       TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_message_device ON im_message_delivery (message_id, device_id);
CREATE INDEX IF NOT EXISTS idx_delivery_conversation ON im_message_delivery (conversation_id, user_id);

CREATE TABLE IF NOT EXISTS im_audit_log
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    operator_id BIGINT       NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(128) NOT NULL,
    detail      VARCHAR(512),
    created_at  TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_operator ON im_audit_log (tenant_id, operator_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_action ON im_audit_log (tenant_id, action, created_at);




