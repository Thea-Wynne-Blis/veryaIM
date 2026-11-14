-- VERYAL V5：审计日志。
-- 只追加，不更新/删除；用于登录与群管理等敏感操作追溯。

CREATE TABLE IF NOT EXISTS im_audit_log
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    operator_id BIGINT       NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(128) NOT NULL,
    detail      VARCHAR(512),
    created_at  DATETIME(3)  NOT NULL,

    KEY idx_audit_tenant_operator (tenant_id, operator_id, created_at),
    KEY idx_audit_action (tenant_id, action, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
