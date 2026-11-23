-- VERYAL V31：个人笔记。
--
-- 笔记只对本人可见。正文按 Markdown 原文存进文件存储（local / MinIO / OSS），
-- 数据库只保留标题、摘要与对象键，避免长文本撑大行记录。
-- 客户端按 updated_at 倒序展示，搜索在 SQL 里对标题与摘要做模糊匹配。

CREATE TABLE IF NOT EXISTS im_note
(
    id         BIGINT       NOT NULL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL DEFAULT '',
    object_key VARCHAR(255) NOT NULL,
    size_bytes BIGINT       NOT NULL DEFAULT 0,
    excerpt    VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,

    KEY idx_note_user_updated (user_id, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
