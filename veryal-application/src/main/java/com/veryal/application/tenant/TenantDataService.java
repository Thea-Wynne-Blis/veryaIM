package com.veryal.application.tenant;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.tenant.Tenant;
import com.veryal.domain.tenant.TenantAdminRepository;
import com.veryal.domain.tenant.TenantDataPurger;
import com.veryal.domain.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 租户数据导出与硬删除。
 */
@Service
@RequiredArgsConstructor
public class TenantDataService {

    private final TenantRepository tenantRepository;
    private final TenantAdminRepository tenantAdminRepository;
    private final TenantAdminService tenantAdminService;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final TenantDataPurger tenantDataPurger;
    private final TimeProvider timeProvider;

    public TenantExportView export(long tenantId) {
        Tenant tenant = requireTenant(tenantId);
        TenantAdminService.TenantUsageView usage = tenantAdminService.usageOf(tenantId);
        List<ExportUser> users = userRepository.findByTenant(tenantId, 10000).stream()
                .map(user -> new ExportUser(user.id(), user.username(), user.nickname(),
                        user.avatar(), user.status().name(), user.createdAt()))
                .toList();
        List<ExportGroup> groups = groupRepository.findByTenant(tenantId, 10000).stream()
                .map(group -> new ExportGroup(group.id(), group.name(), group.ownerId(),
                        group.createdAt()))
                .toList();
        List<Long> admins = tenantAdminRepository.list(tenantId).stream()
                .map(item -> item.userId())
                .toList();
        return new TenantExportView(tenantId, tenant.code(), tenant.name(), tenant.status().name(),
                usage, users, groups, admins, timeProvider.now());
    }

    @Transactional
    public void delete(long tenantId, String confirmCode) {
        Tenant tenant = requireTenant(tenantId);
        if (confirmCode == null || !tenant.code().equals(confirmCode.trim())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "confirmCode must match tenant code");
        }
        tenantDataPurger.purge(tenantId);
    }

    private Tenant requireTenant(long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
    }

    public record TenantExportView(long tenantId, String tenantCode, String tenantName,
                                   String tenantStatus, TenantAdminService.TenantUsageView usage,
                                   List<ExportUser> users, List<ExportGroup> groups,
                                   List<Long> adminUserIds, Instant exportedAt) {
    }

    public record ExportUser(long userId, String username, String nickname, String avatar,
                             String status, Instant createdAt) {
    }

    public record ExportGroup(String groupId, String name, long ownerId, Instant createdAt) {
    }
}
