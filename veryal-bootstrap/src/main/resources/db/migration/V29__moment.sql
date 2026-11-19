-- VERYAL V29：朋友圈（动态、媒体、点赞、评论、可见范围、通知、个人设置）。
--
-- 信息流采用「读时聚合」：不预先给好友写 feed 行，好友关系与可见范围变更立即生效。
-- 媒体只存 im_file 引用，朋友圈自己的鉴权端点负责校验可见性后再读文件内容。

CREATE TABLE IF NOT EXISTS im_moment
(
    id            BIGINT        NOT NULL PRIMARY KEY,
    author_id     BIGINT        NOT NULL,
    content       VARCHAR(2000) NOT NULL DEFAULT '',
    visibility    TINYINT       NOT NULL DEFAULT 0, -- 0 所有朋友 1 私密 2 部分可见 3 不给谁看
    like_count    INT           NOT NULL DEFAULT 0,
    comment_count INT           NOT NULL DEFAULT 0,
    status        TINYINT       NOT NULL DEFAULT 0, -- 0 正常 1 运营下架
    created_at    DATETIME(3)   NOT NULL,
    deleted_at    DATETIME(3)   NULL,

    KEY idx_moment_author (author_id, id),
    KEY idx_moment_feed (status, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_moment_media
(
    id               BIGINT   NOT NULL PRIMARY KEY,
    moment_id        BIGINT   NOT NULL,
    file_id          BIGINT   NOT NULL,
    sort_order       TINYINT  NOT NULL,
    kind             TINYINT  NOT NULL, -- 1 图片 2 视频
    width            INT      NULL,
    height           INT      NULL,
    duration_seconds INT      NULL,

    UNIQUE KEY uk_moment_media_file (moment_id, file_id),
    KEY idx_moment_media_sort (moment_id, sort_order)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_moment_like
(
    moment_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(3) NOT NULL,

    PRIMARY KEY (moment_id, user_id),
    KEY idx_moment_like_user (user_id, moment_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_moment_comment
(
    id               BIGINT       NOT NULL PRIMARY KEY,
    moment_id        BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    reply_to_id      BIGINT       NULL,
    reply_to_user_id BIGINT       NULL,
    content          VARCHAR(500) NOT NULL,
    created_at       DATETIME(3)  NOT NULL,
    deleted_at       DATETIME(3)  NULL,

    KEY idx_moment_comment (moment_id, id),
    KEY idx_moment_comment_user (user_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_moment_audience
(
    moment_id BIGINT  NOT NULL,
    user_id   BIGINT  NOT NULL,
    mode      TINYINT NOT NULL, -- 1 允许（部分可见） 2 排除（不给谁看）

    PRIMARY KEY (moment_id, user_id),
    KEY idx_moment_audience_user (user_id, moment_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_moment_setting
(
    user_id             BIGINT      NOT NULL PRIMARY KEY,
    visible_days        INT         NOT NULL DEFAULT 0, -- 0 全部 3 / 30 / 180
    last_read_moment_id BIGINT      NOT NULL DEFAULT 0,
    updated_at          DATETIME(3) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS im_moment_notice
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    actor_id   BIGINT      NOT NULL,
    type       TINYINT     NOT NULL, -- 1 赞 2 评论 3 回复
    moment_id  BIGINT      NOT NULL,
    comment_id BIGINT      NULL,
    read_at    DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,

    KEY idx_moment_notice_user (user_id, id),
    KEY idx_moment_notice_unread (user_id, read_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
