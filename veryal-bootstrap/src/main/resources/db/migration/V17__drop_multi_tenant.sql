-- VERYAL V17：移除多租户产品表。
-- 说明：历史 tenant_id 列暂时保留为内部固定默认空间（DEFAULT_TENANT_ID=1），
-- 产品层不再提供租户开通、切换、配额、租户管理员和租户数据管理。

DROP TABLE IF EXISTS im_tenant_admin;
DROP TABLE IF EXISTS im_tenant;
