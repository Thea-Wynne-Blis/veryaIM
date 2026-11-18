-- VERYAL V27：用户资料补充「所在地区」与「个性签名」。
-- 资料卡（好友 / 陌生人）和设置页都读这两列，未填写时为 NULL。

ALTER TABLE im_user
    ADD COLUMN region VARCHAR(64) NULL COMMENT '所在地区' AFTER avatar,
    ADD COLUMN signature VARCHAR(255) NULL COMMENT '个性签名' AFTER region;
