-- VERYAL V24：消息引用（回复）。
-- reply_preview 存发送时的原文快照，原消息被撤回后引用内容仍可读。

ALTER TABLE im_message
    ADD COLUMN reply_to_id BIGINT NULL,
    ADD COLUMN reply_sender_id BIGINT NULL,
    ADD COLUMN reply_preview VARCHAR(255) NULL;
