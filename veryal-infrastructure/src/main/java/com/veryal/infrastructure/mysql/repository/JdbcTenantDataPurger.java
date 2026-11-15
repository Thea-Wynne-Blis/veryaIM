package com.veryal.infrastructure.mysql.repository;

import com.veryal.domain.tenant.TenantDataPurger;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 按租户物理删除全部业务数据；文件对象本身不在此处删除。
 */
@Repository
@RequiredArgsConstructor
public class JdbcTenantDataPurger implements TenantDataPurger {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void purge(long tenantId) {
        List<String> statements = List.of(
                "DELETE FROM im_message_delivery WHERE tenant_id = ?",
                "DELETE FROM im_message WHERE tenant_id = ?",
                "DELETE FROM im_conversation_sequence WHERE conversation_id IN "
                        + "(SELECT id FROM im_conversation WHERE tenant_id = ?)",
                "DELETE FROM im_conversation_member WHERE conversation_id IN "
                        + "(SELECT id FROM im_conversation WHERE tenant_id = ?)",
                "DELETE FROM im_conversation WHERE tenant_id = ?",
                "DELETE FROM im_group_mute WHERE group_id IN "
                        + "(SELECT id FROM im_group WHERE tenant_id = ?)",
                "DELETE FROM im_group_setting WHERE tenant_id = ?",
                "DELETE FROM im_group_member WHERE group_id IN "
                        + "(SELECT id FROM im_group WHERE tenant_id = ?)",
                "DELETE FROM im_group WHERE tenant_id = ?",
                "DELETE FROM im_file WHERE tenant_id = ?",
                "DELETE FROM im_upload_session WHERE tenant_id = ?",
                "DELETE FROM im_audit_log WHERE tenant_id = ?",
                "DELETE FROM im_friend_request WHERE tenant_id = ?",
                "DELETE FROM im_friend WHERE tenant_id = ?",
                "DELETE FROM im_blacklist WHERE tenant_id = ?",
                "DELETE FROM im_tenant_admin WHERE tenant_id = ?",
                "DELETE FROM im_device WHERE user_id IN (SELECT id FROM im_user WHERE tenant_id = ?)",
                "DELETE FROM im_user WHERE tenant_id = ?",
                "DELETE FROM im_tenant WHERE id = ?");
        for (String statement : statements) {
            jdbcTemplate.update(statement, tenantId);
        }
    }
}
