-- VERYAL V28：消息收藏。
--
-- 收藏是「快照」而不是引用：原消息被撤回、发送者退群后，收藏里仍然能看到
-- 当时的文字 / 文件名。文件本体仍走 im_file，这里是轻量索引。

CREATE TABLE IF NOT EXISTS im_favorite
(
    id                 BIGINT       NOT NULL PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    conversation_id    VARCHAR(64)  NOT NULL,
    message_id         BIGINT       NOT NULL,
    msg_type           TINYINT      NOT NULL,
    content            MEDIUMBLOB   NULL,
    file_id            BIGINT       NULL,
    file_name          VARCHAR(255) NULL,
    size_bytes         BIGINT       NULL,
    content_type       VARCHAR(128) NULL,
    sender_id          BIGINT       NOT NULL,
    sender_name        VARCHAR(64)  NULL,
    conversation_title VARCHAR(128) NULL,
    created_at         DATETIME(3)  NOT NULL,

    UNIQUE KEY uk_favorite_user_message (user_id, message_id),
    KEY idx_favorite_user (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
