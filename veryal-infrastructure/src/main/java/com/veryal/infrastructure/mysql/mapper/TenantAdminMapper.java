package com.veryal.infrastructure.mysql.mapper;

import com.veryal.infrastructure.mysql.po.TenantAdminPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户管理员 Mapper（复合主键，使用显式 SQL）。
 */
public interface TenantAdminMapper {

    @Select("""
            SELECT tenant_id, user_id, created_at
            FROM im_tenant_admin
            WHERE tenant_id = #{tenantId}
            ORDER BY created_at ASC
            """)
    List<TenantAdminPO> selectByTenant(@Param("tenantId") long tenantId);

    @Select("""
            SELECT COUNT(1)
            FROM im_tenant_admin
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
            """)
    long countOne(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            SELECT COUNT(1)
            FROM im_tenant_admin
            WHERE tenant_id = #{tenantId}
            """)
    long countByTenant(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO im_tenant_admin (tenant_id, user_id, created_at)
            VALUES (#{tenantId}, #{userId}, #{createdAt})
            """)
    int insert(TenantAdminPO po);

    @Delete("""
            DELETE FROM im_tenant_admin
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
            """)
    int delete(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
