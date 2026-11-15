-- VERYAL V9：租户。

CREATE TABLE IF NOT EXISTS im_tenant
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    code              VARCHAR(32)  NOT NULL,
    name              VARCHAR(128) NOT NULL,
    status            TINYINT      NOT NULL DEFAULT 0,
    max_users         INT          NOT NULL DEFAULT 0,
    max_groups        INT          NOT NULL DEFAULT 0,
    max_storage_bytes BIGINT       NOT NULL DEFAULT 0,
    created_at        DATETIME(3)  NOT NULL,
    updated_at        DATETIME(3)  NOT NULL,

    UNIQUE KEY uk_tenant_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO im_tenant (id, code, name, status, max_users, max_groups, max_storage_bytes,
                       created_at, updated_at)
VALUES (1, 'default', 'Default Tenant', 0, 0, 0, 0, NOW(3), NOW(3));
