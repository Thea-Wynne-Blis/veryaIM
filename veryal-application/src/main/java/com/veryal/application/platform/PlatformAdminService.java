package com.veryal.application.platform;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.AuthenticationException;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.security.Totp;
import com.veryal.domain.identity.PasswordHasher;
import com.veryal.domain.platform.PlatformAdmin;
import com.veryal.domain.platform.PlatformAdminRepository;
import com.veryal.domain.platform.PlatformAdminRole;
import com.veryal.domain.platform.PlatformAdminStatus;
import com.veryal.domain.platform.PlatformAuditLog;
import com.veryal.domain.platform.PlatformAuditRepository;
import com.veryal.domain.platform.PlatformRefreshToken;
import com.veryal.domain.platform.PlatformRefreshTokenRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * 平台管理员登录（含失败锁定）、Refresh Token 与账号管理。
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformRefreshTokenRepository refreshTokenRepository;
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
                PlatformAdminRole.PLATFORM_ADMIN, PlatformAdminStatus.ACTIVE, 0, null, null, false,
                now, null));
    }

    public PlatformAuthResult login(String username, String password) {
        return login(username, password, null);
    }

    public PlatformAuthResult login(String username, String password, String mfaCode) {
        PlatformAdmin admin = platformAdminRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException(ErrorCode.AUTHENTICATION_FAILED));
        if (!admin.active()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "platform admin is disabled");
        }
        Instant now = timeProvider.now();
        if (admin.lockedUntil() != null && now.isBefore(admin.lockedUntil())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordHasher.matches(password, admin.passwordHash())) {
            int attempts = admin.failedAttempts() + 1;
            Instant lockedUntil = attempts >= Math.max(1, properties.getMaxLoginAttempts())
                    ? now.plus(properties.getLockDuration()) : null;
            platformAdminRepository.update(new PlatformAdmin(admin.id(), admin.username(),
                    admin.displayName(), admin.passwordHash(), admin.role(), admin.status(),
                    attempts, lockedUntil, admin.mfaSecret(), admin.mfaEnabled(), admin.createdAt(),
                    admin.lastLoginAt()));
            if (lockedUntil != null) {
                auditRecorder.record(admin.id(), "LOGIN_LOCKED", "platform_admin",
                        Long.toString(admin.id()), "attempts=" + attempts);
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }
            throw new AuthenticationException(ErrorCode.AUTHENTICATION_FAILED);
        }
        if (admin.mfaEnabled()) {
            if (mfaCode == null || mfaCode.isBlank()) {
                throw new BusinessException(ErrorCode.MFA_REQUIRED);
            }
            if (!Totp.verify(admin.mfaSecret(), mfaCode.trim(), now.getEpochSecond())) {
                throw new BusinessException(ErrorCode.MFA_INVALID);
            }
        }
        platformAdminRepository.update(new PlatformAdmin(admin.id(), admin.username(),
                admin.displayName(), admin.passwordHash(), admin.role(), admin.status(), 0, null,
                admin.mfaSecret(), admin.mfaEnabled(), admin.createdAt(), now));
        return issueResult(admin, now);
    }

    public MfaSetup setupMfa(PlatformPrincipal operator) {
        PlatformAdmin admin = require(operator.adminId());
        String secret = Totp.generateSecret();
        platformAdminRepository.update(new PlatformAdmin(admin.id(), admin.username(),
                admin.displayName(), admin.passwordHash(), admin.role(), admin.status(),
                admin.failedAttempts(), admin.lockedUntil(), secret, false, admin.createdAt(),
                admin.lastLoginAt()));
        auditRecorder.record(operator.adminId(), "MFA_SETUP", "platform_admin",
                Long.toString(admin.id()), null);
        String url = "otpauth://totp/VERYAL:" + admin.username() + "?secret=" + secret
                + "&issuer=VERYAL&digits=6&period=30";
        return new MfaSetup(secret, url);
    }

    public void enableMfa(PlatformPrincipal operator, String code) {
        PlatformAdmin admin = require(operator.adminId());
        if (admin.mfaSecret() == null || admin.mfaSecret().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "MFA setup required");
        }
        if (!Totp.verify(admin.mfaSecret(), code, timeProvider.now().getEpochSecond())) {
            throw new BusinessException(ErrorCode.MFA_INVALID);
        }
        platformAdminRepository.update(new PlatformAdmin(admin.id(), admin.username(),
                admin.displayName(), admin.passwordHash(), admin.role(), admin.status(),
                admin.failedAttempts(), admin.lockedUntil(), admin.mfaSecret(), true,
                admin.createdAt(), admin.lastLoginAt()));
        auditRecorder.record(operator.adminId(), "MFA_ENABLE", "platform_admin",
                Long.toString(admin.id()), null);
    }

    public void disableMfa(PlatformPrincipal operator, String code) {
        PlatformAdmin admin = require(operator.adminId());
        if (!admin.mfaEnabled() || !Totp.verify(admin.mfaSecret(), code,
                timeProvider.now().getEpochSecond())) {
            throw new BusinessException(ErrorCode.MFA_INVALID);
        }
        platformAdminRepository.update(new PlatformAdmin(admin.id(), admin.username(),
                admin.displayName(), admin.passwordHash(), admin.role(), admin.status(),
                admin.failedAttempts(), admin.lockedUntil(), null, false, admin.createdAt(),
                admin.lastLoginAt()));
        auditRecorder.record(operator.adminId(), "MFA_DISABLE", "platform_admin",
                Long.toString(admin.id()), null);
    }

    public PlatformAuthResult refresh(String refreshToken) {
        String hash = sha256(refreshToken);
        PlatformRefreshToken stored = refreshTokenRepository.findByHash(hash)
                .orElseThrow(() -> new AuthenticationException(ErrorCode.TOKEN_INVALID));
        Instant now = timeProvider.now();
        if (stored.revoked() || !now.isBefore(stored.expiresAt())) {
            throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "refresh token expired");
        }
        PlatformAdmin admin = platformAdminRepository.findById(stored.adminId())
                .orElseThrow(() -> new AuthenticationException(ErrorCode.TOKEN_INVALID));
        if (!admin.active()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "platform admin is disabled");
        }
        refreshTokenRepository.revoke(stored.id());
        return issueResult(admin, now);
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.findByHash(sha256(refreshToken))
                .ifPresent(token -> refreshTokenRepository.revoke(token.id()));
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
                PlatformAdminStatus.ACTIVE, 0, null, null, false, timeProvider.now(), null);
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
                admin.passwordHash(), admin.role(), status, admin.failedAttempts(),
                admin.lockedUntil(), admin.mfaSecret(), admin.mfaEnabled(), admin.createdAt(),
                admin.lastLoginAt());
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
                passwordHasher.hash(password), admin.role(), admin.status(), 0, null,
                admin.mfaSecret(), admin.mfaEnabled(), admin.createdAt(), admin.lastLoginAt());
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
                admin.passwordHash(), role, admin.status(), admin.failedAttempts(),
                admin.lockedUntil(), admin.mfaSecret(), admin.mfaEnabled(), admin.createdAt(),
                admin.lastLoginAt());
        platformAdminRepository.update(updated);
        auditRecorder.record(operator.adminId(), "ADMIN_ROLE_CHANGE", "platform_admin",
                Long.toString(adminId), "role=" + role);
        return updated;
    }

    public List<PlatformAuditLog> auditLogs(PlatformPrincipal operator, int limit) {
        requireAdmin(operator);
        return platformAuditRepository.list(limit);
    }

    private PlatformAuthResult issueResult(PlatformAdmin admin, Instant now) {
        PlatformTokenService.IssuedPlatformToken access = platformTokenService.issue(admin);
        String rawRefresh = randomToken();
        PlatformRefreshToken refresh = new PlatformRefreshToken(idGenerator.nextId(), admin.id(),
                sha256(rawRefresh), now.plus(properties.getRefreshTtl()), false, now);
        refreshTokenRepository.save(refresh);
        return new PlatformAuthResult(access.token(), access.expiresInSeconds(), rawRefresh,
                properties.getRefreshTtl().toSeconds(),
                new PlatformPrincipal(admin.id(), admin.username(), admin.role()), admin.displayName());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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

    public record MfaSetup(String secret, String otpauthUrl) {
    }

    public record PlatformAuthResult(String token, long expiresInSeconds, String refreshToken,
                                     long refreshExpiresInSeconds, PlatformPrincipal principal,
                                     String displayName) {
    }
}
