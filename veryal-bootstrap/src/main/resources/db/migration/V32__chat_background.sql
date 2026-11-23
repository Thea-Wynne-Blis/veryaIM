-- VERYAL V32：聊天背景。
--
-- 每个用户一份全局设置（im_user_setting），每个会话可以单独覆盖（im_conversation_member）。
-- 只存「预设 ID」或「图片文件 ID」，图片本体仍走文件子系统，
-- 且上传时不绑定会话，只有上传者本人能读，保证每个人的背景互相不可见。

CREATE TABLE IF NOT EXISTS im_user_setting
(
    user_id         BIGINT      NOT NULL PRIMARY KEY,
    chat_bg_file_id BIGINT      NULL COMMENT '全局聊天背景图片文件 ID',
    chat_bg_preset  VARCHAR(32) NULL COMMENT '全局聊天背景预设 ID',
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

ALTER TABLE im_conversation_member
    ADD COLUMN chat_bg_file_id BIGINT      NULL COMMENT '该会话的聊天背景图片文件 ID，优先于全局',
    ADD COLUMN chat_bg_preset  VARCHAR(32) NULL COMMENT '该会话的聊天背景预设 ID，优先于全局';
