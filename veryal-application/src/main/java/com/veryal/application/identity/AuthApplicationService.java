package com.veryal.application.identity;

import com.veryal.application.audit.AuditRecorder;
import com.veryal.application.tenant.TenantApplicationService;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.AuthenticationException;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.audit.AuditAction;
import com.veryal.domain.device.Device;
import com.veryal.domain.device.DeviceRepository;
import com.veryal.domain.device.Platform;
import com.veryal.domain.identity.PasswordHasher;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 认证应用服务：注册、登录、Token 校验。
 */
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,32}$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 64;
    private static final int MAX_DEVICE_ID_LENGTH = 64;
    private static final int MAX_DEVICE_NAME_LENGTH = 128;

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenService tokenService;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;
    private final AuditRecorder auditRecorder;
    private final TenantApplicationService tenantApplicationService;

    @Transactional
    public UserProfile register(long tenantId, RegisterCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "username must be 3-32 characters of letters, digits or underscore");
        }
        validatePassword(command.password());
        tenantApplicationService.assertCanAddUser(tenantId);
        if (userRepository.existsByUsername(tenantId, username)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        Instant now = timeProvider.now();
        User user = User.register(idGenerator.nextId(), tenantId, username, command.nickname(),
                passwordHasher.hash(command.password()), now);
        try {
            userRepository.save(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "username already exists", e);
        }
        return UserProfile.from(user);
    }

    @Transactional
    public AuthResult login(long tenantId, LoginCommand command) {
        if (command.deviceId() == null || command.deviceId().isBlank()
                || command.deviceId().length() > MAX_DEVICE_ID_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid deviceId");
        }
        User user = userRepository.findByUsername(tenantId, command.username())
                .orElseThrow(() -> new AuthenticationException(ErrorCode.AUTHENTICATION_FAILED));
        user.ensureActive();
        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            throw new AuthenticationException(ErrorCode.AUTHENTICATION_FAILED);
        }
        Instant now = timeProvider.now();
        Device device = bindDevice(user.id(), command, now);
        IssuedToken issuedToken = tokenService.issue(user.id(), tenantId, device.deviceId());
        auditRecorder.record(tenantId, user.id(), AuditAction.LOGIN, "user",
                Long.toString(user.id()), "device=" + device.deviceId());
        return new AuthResult(UserProfile.from(user), device.deviceId(), issuedToken.token(),
                issuedToken.expiresAt().getEpochSecond() - now.getEpochSecond());
    }

    /**
     * 校验 Token 并加载用户，供 TCP / WebSocket / HTTP 认证入口统一调用。
     */
    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(String token) {
        AuthenticatedUser principal = tokenService.parse(token);
        tenantApplicationService.ensureActive(principal.tenantId());
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new AuthenticationException(ErrorCode.TOKEN_INVALID, "user not found"));
        user.ensureActive();
        return principal;
    }

    private Device bindDevice(long userId, LoginCommand command, Instant now) {
        String deviceId = command.deviceId().trim();
        Platform platform = Platform.from(command.platform());
        String deviceName = command.deviceName();
        if (deviceName != null && deviceName.length() > MAX_DEVICE_NAME_LENGTH) {
            deviceName = deviceName.substring(0, MAX_DEVICE_NAME_LENGTH);
        }
        Device existing = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (existing == null) {
            Device device = Device.bind(idGenerator.nextId(), userId, deviceId, platform, deviceName, now);
            deviceRepository.save(device);
            return device;
        }
        if (existing.userId() != userId) {
            // 设备 ID 已被其他账号绑定，禁止复用，避免设备身份被劫持。
            throw new AuthenticationException(ErrorCode.FORBIDDEN, "device is bound to another user");
        }
        deviceRepository.touchLastLogin(deviceId, now);
        return existing.loginAt(now);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "password length must be between " + MIN_PASSWORD_LENGTH + " and " + MAX_PASSWORD_LENGTH);
        }
    }
}
