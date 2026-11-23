-- V30：头像文件引用。
-- 头像本体走文件子系统（im_file + 本地/对象存储），这里只记 fileId；
-- 对外统一暴露 /api/v1/avatars/user/{userId} 与 /api/v1/avatars/group/{groupId}。
ALTER TABLE im_user
    ADD COLUMN avatar_file_id BIGINT NULL COMMENT '头像文件 ID（im_file.id）' AFTER avatar;

ALTER TABLE im_group
    ADD COLUMN avatar_file_id BIGINT NULL COMMENT '群头像文件 ID（im_file.id）' AFTER name;
