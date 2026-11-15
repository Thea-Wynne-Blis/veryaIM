package com.veryal.application.platform;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.AuthenticationException;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.identity.PasswordHasher;
import com.veryal.domain.platform.PlatformAdmin;
import com.veryal.domain.platform.PlatformAdminRepository;
import com.veryal.domain.platform.PlatformAdminRole;
import com.veryal.domain.platform.PlatformAdminStatus;
import com.veryal.domain.platform.PlatformAuditLog;
import com.veryal.domain.platform.PlatformAuditRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 平台管理员登录、账号管理与启动引导。
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordHasher passwordHasher;
    private final PlatformTokenService platformTokenService;
    private final PlatformAdminProperties properties;
    private final PlatformAuditRecorder auditRecorder;
    private final PlatformAuditRepository platformAuditRepository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @PostConstruct
    void bootstrapAdmin() {
        String username = properties.getBootstrapUsername();
        String password = properties.getBootstrapPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (platformAdminRepository.findByUsername(username).isPresent()) {
            return;
        }
        Instant now = timeProvider.now();
        platformAdminRepository.save(new PlatformAdmin(idGenerator.nextId(), username,
                properties.getBootstrapDisplayName(), passwordHasher.hash(password),
                PlatformAdminRole.PLATFORM_ADMIN, PlatformAdminStatus.ACTIVE, now, null));
    }

    public PlatformAuthResult login(String username, String password) {
        PlatformAdmin admin = platformAdminRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException(ErrorCode.AUTHENTICATION_FAILED));
        if (!admin.active()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "platform admin is disabled");
        }
        if (!passwordHasher.matches(password, admin.passwordHash())) {
            throw new AuthenticationException(ErrorCode.AUTHENTICATION_FAILED);
        }
        PlatformTokenService.IssuedPlatformToken token = platformTokenService.issue(admin);
        platformAdminRepository.updateLastLogin(admin.id(), timeProvider.now());
        return new PlatformAuthResult(token.token(), token.expiresInSeconds(),
                new PlatformPrincipal(admin.id(), admin.username(), admin.role()),
                admin.displayName());
    }

    public PlatformPrincipal authenticate(String token) {
        PlatformPrincipal principal = platformTokenService.parse(token);
        PlatformAdmin admin = platformAdminRepository.findById(principal.adminId())
                .orElseThrow(() -> new AuthenticationException(ErrorCode.TOKEN_INVALID,
                        "platform admin not found"));
        if (!admin.active()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "platform admin is disabled");
        }
        return new PlatformPrincipal(admin.id(), admin.username(), admin.role());
    }

    public List<PlatformAdmin> listAdmins(PlatformPrincipal operator) {
        requireAdmin(operator);
        return platformAdminRepository.findAll(200);
    }

    public PlatformAdmin createAdmin(PlatformPrincipal operator, String username, String password,
                                     String displayName, PlatformAdminRole role) {
        requireAdmin(operator);
        String name = username == null ? "" : username.trim();
        if (!name.matches("[A-Za-z0-9_]{3,32}")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid admin username");
        }
        validatePassword(password);
        if (platformAdminRepository.findByUsername(name).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "admin username already exists");
        }
        PlatformAdmin admin = new PlatformAdmin(idGenerator.nextId(), name, displayName,
                passwordHasher.hash(password), role == null ? PlatformAdminRole.SUPPORT : role,
                PlatformAdminStatus.ACTIVE, timeProvider.now(), null);
        try {
            platformAdminRepository.save(admin);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "admin username already exists");
        }
        auditRecorder.record(operator.adminId(), "ADMIN_CREATE", "platform_admin",
                Long.toString(admin.id()), "username=" + name + ",role=" + admin.role());
        return admin;
    }

    public PlatformAdmin setStatus(PlatformPrincipal operator, long adminId,
                                   PlatformAdminStatus status) {
        requireAdmin(operator);
        if (operator.adminId() == adminId && status == PlatformAdminStatus.DISABLED) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot disable yourself");
        }
        PlatformAdmin admin = require(adminId);
        PlatformAdmin updated = new PlatformAdmin(admin.id(), admin.username(), admin.displayName(),
                admin.passwordHash(), admin.role(), status, admin.createdAt(), admin.lastLoginAt());
        platformAdminRepository.update(updated);
        auditRecorder.record(operator.adminId(), "ADMIN_STATUS", "platform_admin",
                Long.toString(adminId), "status=" + status);
        return updated;
    }

    public void resetPassword(PlatformPrincipal operator, long adminId, String password) {
        requireAdmin(operator);
        validatePassword(password);
        PlatformAdmin admin = require(adminId);
        PlatformAdmin updated = new PlatformAdmin(admin.id(), admin.username(), admin.displayName(),
                passwordHasher.hash(password), admin.role(), admin.status(), admin.createdAt(),
                admin.lastLoginAt());
        platformAdminRepository.update(updated);
        auditRecorder.record(operator.adminId(), "ADMIN_PASSWORD_RESET", "platform_admin",
                Long.toString(adminId), null);
    }

    public PlatformAdmin changeRole(PlatformPrincipal operator, long adminId,
                                    PlatformAdminRole role) {
        requireAdmin(operator);
        if (operator.adminId() == adminId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot change your own role");
        }
        PlatformAdmin admin = require(adminId);
        PlatformAdmin updated = new PlatformAdmin(admin.id(), admin.username(), admin.displayName(),
                admin.passwordHash(), role, admin.status(), admin.createdAt(), admin.lastLoginAt());
        platformAdminRepository.update(updated);
        auditRecorder.record(operator.adminId(), "ADMIN_ROLE_CHANGE", "platform_admin",
                Long.toString(adminId), "role=" + role);
        return updated;
    }

    public List<PlatformAuditLog> auditLogs(PlatformPrincipal operator, int limit) {
        requireAdmin(operator);
        return platformAuditRepository.list(limit);
    }

    private PlatformAdmin require(long adminId) {
        return platformAdminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "platform admin not found"));
    }

    private void requireAdmin(PlatformPrincipal operator) {
        if (operator == null || operator.role() != PlatformAdminRole.PLATFORM_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "platform admin permission required");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "password must be 8-64 characters");
        }
    }

    public record PlatformAuthResult(String token, long expiresInSeconds, PlatformPrincipal principal,
                                     String displayName) {
    }
}
