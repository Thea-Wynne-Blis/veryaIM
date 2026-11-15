-- VERYAL V16：群公告、全员禁言、成员禁言。

CREATE TABLE IF NOT EXISTS im_group_setting
(
    group_id     VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    announcement TEXT         NULL,
    mute_all     TINYINT      NOT NULL DEFAULT 0,
    updated_at   DATETIME(3)  NOT NULL,
    updated_by   BIGINT       NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_group_mute
(
    group_id    VARCHAR(64) NOT NULL,
    user_id     BIGINT      NOT NULL,
    muted_until DATETIME(3) NOT NULL,
    created_at  DATETIME(3) NOT NULL,

    PRIMARY KEY (group_id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
