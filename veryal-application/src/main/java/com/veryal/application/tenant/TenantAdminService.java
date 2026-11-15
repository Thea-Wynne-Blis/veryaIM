package com.veryal.application.tenant;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.tenant.Tenant;
import com.veryal.domain.tenant.TenantAdmin;
import com.veryal.domain.tenant.TenantAdminRepository;
import com.veryal.domain.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户管理员与用量/账单。
 */
@Service
@RequiredArgsConstructor
public class TenantAdminService {

    private static final long GIB = 1024L * 1024 * 1024;

    private final TenantAdminRepository tenantAdminRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final FileRepository fileRepository;
    private final MessageRepository messageRepository;
    private final BillingProperties billingProperties;
    private final TimeProvider timeProvider;

    public boolean isAdmin(long tenantId, long userId) {
        return tenantAdminRepository.exists(tenantId, userId);
    }

    public List<TenantAdminView> list(long tenantId, long userId) {
        requireAdmin(tenantId, userId);
        return tenantAdminRepository.list(tenantId).stream()
                .map(item -> userRepository.findById(item.userId())
                        .map(user -> new TenantAdminView(user.id(), user.nickname(), user.avatar()))
                        .orElse(new TenantAdminView(item.userId(), "", null)))
                .toList();
    }

    @Transactional
    public TenantAdminView add(long tenantId, long operatorId, long userId) {
        requireAdmin(tenantId, operatorId);
        User user = requireTenantUser(tenantId, userId);
        if (!tenantAdminRepository.exists(tenantId, userId)) {
            tenantAdminRepository.save(new TenantAdmin(tenantId, userId, timeProvider.now()));
        }
        return new TenantAdminView(user.id(), user.nickname(), user.avatar());
    }

    @Transactional
    public void remove(long tenantId, long operatorId, long userId) {
        requireAdmin(tenantId, operatorId);
        if (operatorId == userId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot remove yourself");
        }
        tenantAdminRepository.delete(tenantId, userId);
    }

    /**
     * 平台管理员任命首个租户管理员，不做租户内权限校验。
     */
    @Transactional
    public TenantAdminView appoint(long tenantId, long userId) {
        User user = requireTenantUser(tenantId, userId);
        if (!tenantAdminRepository.exists(tenantId, userId)) {
            tenantAdminRepository.save(new TenantAdmin(tenantId, userId, timeProvider.now()));
        }
        return new TenantAdminView(user.id(), user.nickname(), user.avatar());
    }

    public TenantUsageView usage(long tenantId, long operatorId) {
        requireAdmin(tenantId, operatorId);
        return usageOf(tenantId);
    }

    public TenantUsageView usageOf(long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
        long users = userRepository.count(tenantId);
        long groups = groupRepository.countByTenant(tenantId);
        long messages = messageRepository.countByTenant(tenantId);
        long storageBytes = fileRepository.totalBytes(tenantId);
        long admins = tenantAdminRepository.count(tenantId);
        long storageGb = (storageBytes + GIB - 1) / GIB;
        long estimated = users * billingProperties.getUserCents()
                + groups * billingProperties.getGroupCents()
                + storageGb * billingProperties.getStorageGbCents();
        return new TenantUsageView(tenant.id(), tenant.code(), users, groups, messages,
                storageBytes, admins, estimated);
    }

    private void requireAdmin(long tenantId, long userId) {
        if (!tenantAdminRepository.exists(tenantId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "tenant admin permission required");
        }
    }

    private User requireTenantUser(long tenantId, long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.tenantId() == tenantId)
                .filter(User::active)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public record TenantAdminView(long userId, String nickname, String avatar) {
    }

    public record TenantUsageView(long tenantId, String tenantCode, long users, long groups,
                                  long messages, long storageBytes, long admins,
                                  long estimatedMonthlyCents) {
    }
}
