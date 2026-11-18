-- 永久禁言：muted_until 为 NULL 表示永久，需要允许 NULL。
-- 限时禁言仍然写到期时间，读路径用「行存在 + 到期时间/永久」判断。

ALTER TABLE im_group_mute
    MODIFY COLUMN muted_until DATETIME(3) NULL COMMENT '禁言到期时间；NULL 表示永久禁言';
