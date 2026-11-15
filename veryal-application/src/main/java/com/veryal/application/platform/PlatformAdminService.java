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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 平台管理员登录、Token 校验与启动引导。
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordHasher passwordHasher;
    private final PlatformTokenService platformTokenService;
    private final PlatformAdminProperties properties;
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

    public record PlatformAuthResult(String token, long expiresInSeconds, PlatformPrincipal principal,
                                     String displayName) {
    }
}
