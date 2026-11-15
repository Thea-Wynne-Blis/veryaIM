-- VERYAL V21：彻底移除数据库 tenant_id。
-- 单平台消费级 IM，用户名/好友/会话/消息均全局唯一。

ALTER TABLE im_user
    DROP INDEX uk_user_tenant_username,
    DROP COLUMN tenant_id,
    ADD UNIQUE KEY uk_username (username);

ALTER TABLE im_conversation
    DROP INDEX idx_conversation_tenant_type,
    DROP COLUMN tenant_id,
    ADD KEY idx_conversation_type (conversation_type, updated_at);

ALTER TABLE im_message
    DROP INDEX uk_sender_client_msg,
    DROP INDEX uk_conversation_seq,
    DROP COLUMN tenant_id,
    ADD UNIQUE KEY uk_sender_client_msg (sender_id, client_msg_id),
    ADD UNIQUE KEY uk_conversation_seq (conversation_id, seq);

ALTER TABLE im_group
    DROP INDEX idx_group_tenant,
    DROP COLUMN tenant_id,
    ADD KEY idx_group_owner (owner_id);

ALTER TABLE im_message_delivery
    DROP COLUMN tenant_id;

ALTER TABLE im_audit_log
    DROP INDEX idx_audit_tenant_operator,
    DROP INDEX idx_audit_action,
    DROP COLUMN tenant_id,
    ADD KEY idx_audit_operator (operator_id, created_at),
    ADD KEY idx_audit_action (action, created_at);

ALTER TABLE im_file
    DROP INDEX idx_file_tenant_uploader,
    DROP COLUMN tenant_id,
    ADD KEY idx_file_uploader (uploader_id, created_at);

ALTER TABLE im_upload_session
    DROP INDEX idx_upload_tenant_uploader,
    DROP COLUMN tenant_id,
    ADD KEY idx_upload_uploader (uploader_id, created_at);

ALTER TABLE im_friend_request
    DROP INDEX uk_friend_request_direction,
    DROP INDEX idx_friend_request_to,
    DROP COLUMN tenant_id,
    ADD UNIQUE KEY uk_friend_request_direction (from_user_id, to_user_id),
    ADD KEY idx_friend_request_to (to_user_id, status, updated_at);

ALTER TABLE im_friend
    DROP INDEX uk_friend_pair,
    DROP INDEX idx_friend_user,
    DROP COLUMN tenant_id,
    ADD UNIQUE KEY uk_friend_pair (user_id, friend_id),
    ADD KEY idx_friend_user (user_id);

ALTER TABLE im_blacklist
    DROP INDEX uk_blacklist_pair,
    DROP INDEX idx_blacklist_user,
    DROP COLUMN tenant_id,
    ADD UNIQUE KEY uk_blacklist_pair (user_id, blocked_user_id),
    ADD KEY idx_blacklist_user (user_id);

ALTER TABLE im_group_setting
    DROP COLUMN tenant_id;
