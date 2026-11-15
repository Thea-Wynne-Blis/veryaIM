-- VERYAL V15：租户管理员。

CREATE TABLE IF NOT EXISTS im_tenant_admin
(
    tenant_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(3) NOT NULL,

    PRIMARY KEY (tenant_id, user_id),
    KEY idx_tenant_admin_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
