-- VERYAL V14：平台管理员 MFA（TOTP）。

ALTER TABLE im_platform_admin
    ADD COLUMN mfa_secret VARCHAR(64) NULL,
    ADD COLUMN mfa_enabled TINYINT NOT NULL DEFAULT 0;
