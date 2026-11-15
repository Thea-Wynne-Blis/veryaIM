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
    created_at      TIMESTAMP(3) NOT NULL,
    recalled_at     TIMESTAMP(3) NULL,
    recalled_by     BIGINT       NULL
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

CREATE TABLE IF NOT EXISTS im_file
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    uploader_id   BIGINT       NOT NULL,
    object_key    VARCHAR(512) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(128) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    storage_type  VARCHAR(16)  NOT NULL,
    status        TINYINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(3) NOT NULL,
    deleted_at    TIMESTAMP(3) NULL,
    conversation_id VARCHAR(64) NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_file_object_key ON im_file (object_key);
CREATE INDEX IF NOT EXISTS idx_file_tenant_uploader ON im_file (tenant_id, uploader_id, created_at);
CREATE INDEX IF NOT EXISTS idx_file_conversation ON im_file (conversation_id);

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
    created_at      TIMESTAMP(3) NOT NULL,
    file_id         BIGINT       NULL
);
CREATE INDEX IF NOT EXISTS idx_upload_tenant_uploader ON im_upload_session (tenant_id, uploader_id, created_at);

CREATE TABLE IF NOT EXISTS im_friend_request
(
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    from_user_id BIGINT       NOT NULL,
    to_user_id   BIGINT       NOT NULL,
    message      VARCHAR(255) NULL,
    status       TINYINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP(3) NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_request_direction
    ON im_friend_request (tenant_id, from_user_id, to_user_id);
CREATE INDEX IF NOT EXISTS idx_friend_request_to
    ON im_friend_request (tenant_id, to_user_id, status, updated_at);

CREATE TABLE IF NOT EXISTS im_friend
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    tenant_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    friend_id  BIGINT      NOT NULL,
    remark     VARCHAR(64) NULL,
    created_at TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_pair ON im_friend (tenant_id, user_id, friend_id);
CREATE INDEX IF NOT EXISTS idx_friend_user ON im_friend (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS im_blacklist
(
    id              BIGINT      NOT NULL PRIMARY KEY,
    tenant_id       BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    blocked_user_id BIGINT      NOT NULL,
    created_at      TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_blacklist_pair
    ON im_blacklist (tenant_id, user_id, blocked_user_id);
CREATE INDEX IF NOT EXISTS idx_blacklist_user ON im_blacklist (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS im_platform_admin
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    display_name  VARCHAR(64)  NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    status        TINYINT      NOT NULL DEFAULT 0,
    failed_attempts INT        NOT NULL DEFAULT 0,
    locked_until  TIMESTAMP(3) NULL,
    mfa_secret    VARCHAR(64)  NULL,
    mfa_enabled   TINYINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(3) NOT NULL,
    last_login_at TIMESTAMP(3) NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_admin_username ON im_platform_admin (username);

CREATE TABLE IF NOT EXISTS im_platform_refresh_token
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    admin_id   BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    revoked    TINYINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_refresh_hash ON im_platform_refresh_token (token_hash);
CREATE INDEX IF NOT EXISTS idx_platform_refresh_admin ON im_platform_refresh_token (admin_id, expires_at);

CREATE TABLE IF NOT EXISTS im_platform_audit_log
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    admin_id    BIGINT       NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(128) NOT NULL,
    detail      VARCHAR(512) NULL,
    created_at  TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_platform_audit_admin ON im_platform_audit_log (admin_id, created_at);

CREATE TABLE IF NOT EXISTS im_group_setting
(
    group_id     VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    announcement TEXT         NULL,
    mute_all     TINYINT      NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP(3) NOT NULL,
    updated_by   BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS im_group_mute
(
    group_id    VARCHAR(64) NOT NULL,
    user_id     BIGINT      NOT NULL,
    muted_until TIMESTAMP(3) NOT NULL,
    created_at  TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS im_user_privacy
(
    user_id            BIGINT      NOT NULL PRIMARY KEY,
    search_by_phone    TINYINT     NOT NULL DEFAULT 1,
    search_by_username TINYINT     NOT NULL DEFAULT 1,
    friend_policy      TINYINT     NOT NULL DEFAULT 1,
    show_online        TINYINT     NOT NULL DEFAULT 1,
    read_receipt       TINYINT     NOT NULL DEFAULT 1,
    invited_to_group   TINYINT     NOT NULL DEFAULT 1,
    updated_at         TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS im_report
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    reporter_id BIGINT       NOT NULL,
    target_type VARCHAR(16)  NOT NULL,
    target_id   VARCHAR(64)  NOT NULL,
    reason      VARCHAR(64)  NOT NULL,
    detail      VARCHAR(512) NULL,
    status      TINYINT      NOT NULL DEFAULT 0,
    handled_by  BIGINT       NULL,
    handled_at  TIMESTAMP(3) NULL,
    created_at  TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_report_status ON im_report (status, created_at);
CREATE INDEX IF NOT EXISTS idx_report_target ON im_report (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_report_reporter ON im_report (reporter_id, created_at);




