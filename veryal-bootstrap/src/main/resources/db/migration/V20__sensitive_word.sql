-- VERYAL V20：敏感词词库。

CREATE TABLE IF NOT EXISTS im_sensitive_word
(
    id         BIGINT       NOT NULL PRIMARY KEY,
    word       VARCHAR(64)  NOT NULL,
    enabled    TINYINT      NOT NULL DEFAULT 1,
    created_at DATETIME(3)  NOT NULL,

    UNIQUE KEY uk_sensitive_word (word)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
