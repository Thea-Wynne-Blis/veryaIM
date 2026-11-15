-- VERYAL V19：用户全局禁言。

CREATE TABLE IF NOT EXISTS im_user_mute
(
    user_id     BIGINT       NOT NULL PRIMARY KEY,
    muted_until DATETIME(3)  NOT NULL,
    reason      VARCHAR(255) NULL,
    updated_by  BIGINT       NOT NULL,
    updated_at  DATETIME(3)  NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
