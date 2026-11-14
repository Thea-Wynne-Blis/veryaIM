-- VERYAL V4：消息设备投递记录。
-- 同一消息同一设备只保留一条记录，delivered_sequence 只前进。

CREATE TABLE IF NOT EXISTS im_message_delivery
(
    id                 BIGINT      NOT NULL PRIMARY KEY,
    tenant_id          BIGINT      NOT NULL,
    message_id         BIGINT      NOT NULL,
    conversation_id    VARCHAR(64) NOT NULL,
    user_id            BIGINT      NOT NULL,
    device_id          VARCHAR(64) NOT NULL,
    delivered_sequence BIGINT      NOT NULL DEFAULT 0,
    delivered_at       DATETIME(3) NOT NULL,

    UNIQUE KEY uk_delivery_message_device (message_id, device_id),
    KEY idx_delivery_conversation (conversation_id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
