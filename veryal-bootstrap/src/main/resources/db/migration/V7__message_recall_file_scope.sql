-- VERYAL V7：消息撤回 + 文件会话归属。

ALTER TABLE im_message
    ADD COLUMN recalled_at DATETIME(3) NULL,
    ADD COLUMN recalled_by BIGINT NULL;

ALTER TABLE im_file
    ADD COLUMN conversation_id VARCHAR(64) NULL;

CREATE INDEX idx_file_conversation ON im_file (conversation_id);
