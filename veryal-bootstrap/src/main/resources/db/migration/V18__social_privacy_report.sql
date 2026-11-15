-- VERYAL V18：消费级社交隐私与举报。

CREATE TABLE IF NOT EXISTS im_user_privacy
(
    user_id            BIGINT      NOT NULL PRIMARY KEY,
    search_by_phone    TINYINT     NOT NULL DEFAULT 1,
    search_by_username TINYINT     NOT NULL DEFAULT 1,
    friend_policy      TINYINT     NOT NULL DEFAULT 1,
    show_online        TINYINT     NOT NULL DEFAULT 1,
    read_receipt       TINYINT     NOT NULL DEFAULT 1,
    invited_to_group   TINYINT     NOT NULL DEFAULT 1,
    updated_at         DATETIME(3) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

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
    handled_at  DATETIME(3)  NULL,
    created_at  DATETIME(3)  NOT NULL,

    KEY idx_report_status (status, created_at),
    KEY idx_report_target (target_type, target_id),
    KEY idx_report_reporter (reporter_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
