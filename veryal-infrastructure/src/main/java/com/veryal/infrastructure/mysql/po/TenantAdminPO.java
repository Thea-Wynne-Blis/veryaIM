package com.veryal.infrastructure.mysql.po;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_tenant_admin 映射对象。
 */
@Getter
@Setter
public class TenantAdminPO {

    public long tenantId;
    public long userId;
    public LocalDateTime createdAt;
}
