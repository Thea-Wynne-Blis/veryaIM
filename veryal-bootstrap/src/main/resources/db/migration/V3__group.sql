-- VERYAL V3：群组基础表。
-- 群 ID 与群会话 ID 保持一致，消息权限复用会话成员。

CREATE TABLE IF NOT EXISTS im_group
(
    id         VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL,
    name       VARCHAR(128) NOT NULL,
    owner_id   BIGINT       NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,

    KEY idx_group_tenant (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_group_member
(
    id        BIGINT      NOT NULL PRIMARY KEY,
    group_id  VARCHAR(64) NOT NULL,
    user_id   BIGINT      NOT NULL,
    role      TINYINT     NOT NULL DEFAULT 0,
    joined_at DATETIME(3) NOT NULL,

    UNIQUE KEY uk_group_user (group_id, user_id),
    KEY idx_group_member_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
