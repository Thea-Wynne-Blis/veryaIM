-- 会话列表的成员级设置：置顶、免打扰、不显示、清空记录水位、手动未读。
-- 全部是「每个人自己的一份」，互不影响：清空记录只是抬高本人可见水位，
-- 消息行仍在库里，会话另一方的记录不受影响。

ALTER TABLE im_conversation_member
    ADD COLUMN pinned        TINYINT     NOT NULL DEFAULT 0 COMMENT '是否置顶',
    ADD COLUMN muted         TINYINT     NOT NULL DEFAULT 0 COMMENT '消息免打扰',
    ADD COLUMN hidden_at     DATETIME(3) NULL COMMENT '不显示的设置时间；更晚的消息会重新把会话顶回来',
    ADD COLUMN cleared_seq   BIGINT      NOT NULL DEFAULT 0 COMMENT '本人清空的聊天记录水位（seq <= 此值的消息对本人不可见）',
    ADD COLUMN manual_unread TINYINT     NOT NULL DEFAULT 0 COMMENT '手动标为未读';
