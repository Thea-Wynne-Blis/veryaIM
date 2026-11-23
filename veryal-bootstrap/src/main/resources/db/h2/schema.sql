-- VERYAL H2 schema（MODE=MySQL），仅用于本地开发与集成测试。
-- 生产环境请使用 Flyway + MySQL 迁移脚本（db/migration）。

CREATE TABLE IF NOT EXISTS im_user
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    nickname      VARCHAR(128),
    avatar        VARCHAR(512),
    region        VARCHAR(64),
    signature     VARCHAR(255),
    status        TINYINT      NOT NULL DEFAULT 1,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP(3) NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_username ON im_user (username);

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
    conversation_type TINYINT     NOT NULL,
    created_at        TIMESTAMP(3) NOT NULL,
    updated_at        TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_conversation_type ON im_conversation (conversation_type, updated_at);

CREATE TABLE IF NOT EXISTS im_conversation_member
(
    id              BIGINT      NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    user_id         BIGINT      NOT NULL,
    last_read_seq   BIGINT      NOT NULL DEFAULT 0,
    last_sync_seq   BIGINT      NOT NULL DEFAULT 0,
    joined_at       TIMESTAMP(3) NOT NULL,
    pinned          TINYINT     NOT NULL DEFAULT 0,
    muted           TINYINT     NOT NULL DEFAULT 0,
    hidden_at       TIMESTAMP(3) NULL,
    cleared_seq     BIGINT      NOT NULL DEFAULT 0,
    manual_unread   TINYINT     NOT NULL DEFAULT 0
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
    conversation_id VARCHAR(64)  NOT NULL,
    sender_id       BIGINT       NOT NULL,
    client_msg_id   VARCHAR(64)  NOT NULL,
    seq             BIGINT       NOT NULL,
    msg_type        SMALLINT     NOT NULL,
    content         BLOB         NOT NULL,
    created_at      TIMESTAMP(3) NOT NULL,
    recalled_at     TIMESTAMP(3) NULL,
    recalled_by     BIGINT       NULL,
    reply_to_id     BIGINT       NULL,
    reply_sender_id BIGINT       NULL,
    reply_preview   VARCHAR(255) NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sender_client_msg ON im_message (sender_id, client_msg_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_seq ON im_message (conversation_id, seq);
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
    name       VARCHAR(128) NOT NULL,
    owner_id   BIGINT       NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_group_owner ON im_group (owner_id);

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
    operator_id BIGINT       NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(128) NOT NULL,
    detail      VARCHAR(512),
    created_at  TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_operator ON im_audit_log (operator_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_action ON im_audit_log (action, created_at);

CREATE TABLE IF NOT EXISTS im_file
(
    id            BIGINT       NOT NULL PRIMARY KEY,
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
CREATE INDEX IF NOT EXISTS idx_file_uploader ON im_file (uploader_id, created_at);
CREATE INDEX IF NOT EXISTS idx_file_conversation ON im_file (conversation_id);

CREATE TABLE IF NOT EXISTS im_upload_session
(
    id              BIGINT       NOT NULL PRIMARY KEY,
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
CREATE INDEX IF NOT EXISTS idx_upload_uploader ON im_upload_session (uploader_id, created_at);

CREATE TABLE IF NOT EXISTS im_friend_request
(
    id           BIGINT       NOT NULL PRIMARY KEY,
    from_user_id BIGINT       NOT NULL,
    to_user_id   BIGINT       NOT NULL,
    message      VARCHAR(255) NULL,
    status       TINYINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP(3) NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_request_direction
    ON im_friend_request (from_user_id, to_user_id);
CREATE INDEX IF NOT EXISTS idx_friend_request_to
    ON im_friend_request (to_user_id, status, updated_at);

CREATE TABLE IF NOT EXISTS im_friend
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    friend_id  BIGINT      NOT NULL,
    remark     VARCHAR(64) NULL,
    created_at TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_pair ON im_friend (user_id, friend_id);
CREATE INDEX IF NOT EXISTS idx_friend_user ON im_friend (user_id);

CREATE TABLE IF NOT EXISTS im_blacklist
(
    id              BIGINT      NOT NULL PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    blocked_user_id BIGINT      NOT NULL,
    created_at      TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_blacklist_pair
    ON im_blacklist (user_id, blocked_user_id);
CREATE INDEX IF NOT EXISTS idx_blacklist_user ON im_blacklist (user_id);

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
    announcement TEXT         NULL,
    mute_all     TINYINT      NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP(3) NOT NULL,
    updated_by   BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS im_group_mute
(
    group_id    VARCHAR(64) NOT NULL,
    user_id     BIGINT      NOT NULL,
    muted_until TIMESTAMP(3) NULL,
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

CREATE TABLE IF NOT EXISTS im_user_mute
(
    user_id     BIGINT       NOT NULL PRIMARY KEY,
    muted_until TIMESTAMP(3) NOT NULL,
    reason      VARCHAR(255) NULL,
    updated_by  BIGINT       NOT NULL,
    updated_at  TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS im_sensitive_word
(
    id         BIGINT       NOT NULL PRIMARY KEY,
    word       VARCHAR(64)  NOT NULL,
    enabled    TINYINT      NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sensitive_word ON im_sensitive_word (word);

CREATE TABLE IF NOT EXISTS im_push_device
(
    id         BIGINT       NOT NULL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    device_id  VARCHAR(64)  NOT NULL,
    platform   VARCHAR(16)  NOT NULL,
    token      VARCHAR(512) NOT NULL,
    enabled    TINYINT      NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_push_user_device ON im_push_device (user_id, device_id);
CREATE INDEX IF NOT EXISTS idx_push_user_enabled ON im_push_device (user_id, enabled);
CREATE INDEX IF NOT EXISTS idx_push_platform ON im_push_device (platform, enabled);

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
    created_at    TIMESTAMP(3) NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_web_push_endpoint_hash ON im_web_push_subscription (endpoint_hash);
CREATE INDEX IF NOT EXISTS idx_web_push_user ON im_web_push_subscription (user_id, enabled);





CREATE TABLE IF NOT EXISTS im_favorite
(
    id                 BIGINT       NOT NULL PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    conversation_id    VARCHAR(64)  NOT NULL,
    message_id         BIGINT       NOT NULL,
    msg_type           INT          NOT NULL,
    content            BLOB         NULL,
    file_id            BIGINT       NULL,
    file_name          VARCHAR(255) NULL,
    size_bytes         BIGINT       NULL,
    content_type       VARCHAR(128) NULL,
    sender_id          BIGINT       NOT NULL,
    sender_name        VARCHAR(64)  NULL,
    conversation_title VARCHAR(128) NULL,
    created_at         TIMESTAMP(3) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_favorite_user_message ON im_favorite (user_id, message_id);
CREATE INDEX IF NOT EXISTS idx_favorite_user ON im_favorite (user_id, created_at);

-- 朋友圈（与 V29__moment.sql 对齐）：dev 环境此前漏了这几张表，
-- 导致朋友圈接口和孤儿文件清理任务在 H2 下直接报表不存在。
CREATE TABLE IF NOT EXISTS im_moment
(
    id            BIGINT        NOT NULL PRIMARY KEY,
    author_id     BIGINT        NOT NULL,
    content       VARCHAR(2000) NOT NULL DEFAULT '',
    visibility    TINYINT       NOT NULL DEFAULT 0,
    like_count    INT           NOT NULL DEFAULT 0,
    comment_count INT           NOT NULL DEFAULT 0,
    status        TINYINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(3)  NOT NULL,
    deleted_at    TIMESTAMP(3)  NULL
);
CREATE INDEX IF NOT EXISTS idx_moment_author ON im_moment (author_id, id);
CREATE INDEX IF NOT EXISTS idx_moment_feed ON im_moment (status, id);

CREATE TABLE IF NOT EXISTS im_moment_media
(
    id               BIGINT  NOT NULL PRIMARY KEY,
    moment_id        BIGINT  NOT NULL,
    file_id          BIGINT  NOT NULL,
    sort_order       TINYINT NOT NULL,
    kind             TINYINT NOT NULL,
    width            INT     NULL,
    height           INT     NULL,
    duration_seconds INT     NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_moment_media_file ON im_moment_media (moment_id, file_id);
CREATE INDEX IF NOT EXISTS idx_moment_media_sort ON im_moment_media (moment_id, sort_order);

CREATE TABLE IF NOT EXISTS im_moment_like
(
    moment_id  BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (moment_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_moment_like_user ON im_moment_like (user_id, moment_id);

CREATE TABLE IF NOT EXISTS im_moment_comment
(
    id               BIGINT       NOT NULL PRIMARY KEY,
    moment_id        BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    reply_to_id      BIGINT       NULL,
    reply_to_user_id BIGINT       NULL,
    content          VARCHAR(500) NOT NULL,
    created_at       TIMESTAMP(3) NOT NULL,
    deleted_at       TIMESTAMP(3) NULL
);
CREATE INDEX IF NOT EXISTS idx_moment_comment ON im_moment_comment (moment_id, id);
CREATE INDEX IF NOT EXISTS idx_moment_comment_user ON im_moment_comment (user_id, id);

CREATE TABLE IF NOT EXISTS im_moment_audience
(
    moment_id BIGINT  NOT NULL,
    user_id   BIGINT  NOT NULL,
    mode      TINYINT NOT NULL,
    PRIMARY KEY (moment_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_moment_audience_user ON im_moment_audience (user_id, moment_id);

CREATE TABLE IF NOT EXISTS im_moment_setting
(
    user_id             BIGINT       NOT NULL PRIMARY KEY,
    visible_days        INT          NOT NULL DEFAULT 0,
    last_read_moment_id BIGINT       NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS im_moment_notice
(
    id         BIGINT       NOT NULL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    actor_id   BIGINT       NOT NULL,
    type       TINYINT      NOT NULL,
    moment_id  BIGINT       NOT NULL,
    comment_id BIGINT       NULL,
    read_at    TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_moment_notice_user ON im_moment_notice (user_id, id);
CREATE INDEX IF NOT EXISTS idx_moment_notice_unread ON im_moment_notice (user_id, read_at);
