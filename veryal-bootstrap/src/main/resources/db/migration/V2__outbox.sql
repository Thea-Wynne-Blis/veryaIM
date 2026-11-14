-- VERYAL V2：Outbox 可靠事件表。
-- status：0 待发布、1 已发布、2 等待重试、3 失败、4 发布中（扩展状态，用于多实例抢占）。

CREATE TABLE IF NOT EXISTS im_outbox
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    event_id      VARCHAR(64)  NOT NULL,
    aggregate_id  VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(64)  NOT NULL,
    payload       MEDIUMBLOB   NOT NULL,
    status        TINYINT      NOT NULL DEFAULT 0,
    retry_count   INT          NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3)  NOT NULL,
    last_error    VARCHAR(512),
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    published_at  DATETIME(3),

    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_status_retry (status, next_retry_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
