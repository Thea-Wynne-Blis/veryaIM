package com.veryal.application.tenant;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.tenant.Tenant;
import com.veryal.domain.tenant.TenantRepository;
import com.veryal.domain.tenant.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 租户解析、基础配额与平台侧租户管理。
 */
@Service
@RequiredArgsConstructor
public class TenantApplicationService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final FileRepository fileRepository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public Tenant resolveActive(String tenantCode) {
        String code = tenantCode == null || tenantCode.isBlank()
                ? "default" : tenantCode.trim().toLowerCase(Locale.ROOT);
        Tenant tenant = tenantRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND,
                        "tenant not found: " + code));
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TENANT_DISABLED, "tenant is disabled: " + code);
        }
        return tenant;
    }

    public Tenant require(long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
    }

    public void assertCanAddUser(long tenantId) {
        Tenant tenant = require(tenantId);
        if (tenant.maxUsers() > 0 && userRepository.count(tenantId) >= tenant.maxUsers()) {
            throw new BusinessException(ErrorCode.TENANT_QUOTA_EXCEEDED,
                    "tenant user quota exceeded: " + tenant.maxUsers());
        }
    }

    public void assertCanCreateGroup(long tenantId) {
        Tenant tenant = require(tenantId);
        if (tenant.maxGroups() > 0 && groupRepository.countByTenant(tenantId) >= tenant.maxGroups()) {
            throw new BusinessException(ErrorCode.TENANT_QUOTA_EXCEEDED,
                    "tenant group quota exceeded: " + tenant.maxGroups());
        }
    }

    public void assertStorage(long tenantId, long additionalBytes) {
        Tenant tenant = require(tenantId);
        if (tenant.maxStorageBytes() <= 0) {
            return;
        }
        long used = fileRepository.totalBytes(tenantId);
        if (used + additionalBytes > tenant.maxStorageBytes()) {
            throw new BusinessException(ErrorCode.TENANT_QUOTA_EXCEEDED,
                    "tenant storage quota exceeded: " + tenant.maxStorageBytes());
        }
    }

    public Tenant create(String code, String name, int maxUsers, int maxGroups,
                         long maxStorageBytes) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (tenantRepository.findByCode(normalized).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant code already exists");
        }
        Instant now = timeProvider.now();
        Tenant tenant = new Tenant(idGenerator.nextId(), normalized, name, TenantStatus.ACTIVE,
                Math.max(0, maxUsers), Math.max(0, maxGroups), Math.max(0, maxStorageBytes), now, now);
        tenantRepository.save(tenant);
        return tenant;
    }

    public Tenant updateStatus(long tenantId, TenantStatus status) {
        Tenant tenant = require(tenantId);
        Tenant updated = new Tenant(tenant.id(), tenant.code(), tenant.name(), status,
                tenant.maxUsers(), tenant.maxGroups(), tenant.maxStorageBytes(),
                tenant.createdAt(), timeProvider.now());
        tenantRepository.update(updated);
        return updated;
    }

    public List<Tenant> list(int limit) {
        return tenantRepository.findAll(limit);
    }
}
