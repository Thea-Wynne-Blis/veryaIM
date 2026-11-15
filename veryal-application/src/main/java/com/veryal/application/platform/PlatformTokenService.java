package com.veryal.application.platform;

import com.veryal.application.identity.TokenProperties;
import com.veryal.common.core.RandomIds;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.AuthenticationException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.platform.PlatformAdmin;
import com.veryal.domain.platform.PlatformAdminRole;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * 平台管理员 Token（HS256）。claims 带 scope=PLATFORM，且不包含租户用户 tid。
 */
public class PlatformTokenService {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String secret;
    private final long ttlSeconds;
    private final TimeProvider timeProvider;

    public PlatformTokenService(PlatformAdminProperties properties, TokenProperties tokenProperties,
                                TimeProvider timeProvider) {
        String configured = properties.getTokenSecret();
        this.secret = configured == null || configured.length() < 32
                ? tokenProperties.secret() : configured;
        this.ttlSeconds = Math.max(60, properties.getTokenTtl().toSeconds());
        this.timeProvider = timeProvider;
    }

    public IssuedPlatformToken issue(PlatformAdmin admin) {
        Instant now = timeProvider.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String payload = JsonUtils.toJson(Map.of(
                "sub", admin.id(),
                "username", admin.username(),
                "role", admin.role().name(),
                "scope", "PLATFORM",
                "jti", RandomIds.newId(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond()));
        String signingInput = encode(HEADER_JSON) + "." + encode(payload);
        String token = signingInput + "." + encode(sign(signingInput));
        return new IssuedPlatformToken(token, ttlSeconds, expiresAt);
    }

    public PlatformPrincipal parse(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "missing platform token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "malformed platform token");
        }
        try {
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "invalid platform signature");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = JsonUtils.mapper().readValue(
                    DECODER.decode(parts[1]), Map.class);
            if (!"PLATFORM".equals(claims.get("scope"))) {
                throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "not a platform token");
            }
            long exp = ((Number) claims.get("exp")).longValue();
            if (timeProvider.now().getEpochSecond() >= exp) {
                throw new AuthenticationException(ErrorCode.TOKEN_EXPIRED);
            }
            long adminId = ((Number) claims.get("sub")).longValue();
            String username = String.valueOf(claims.get("username"));
            PlatformAdminRole role = PlatformAdminRole.valueOf(String.valueOf(claims.get("role")));
            return new PlatformPrincipal(adminId, username, role);
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "invalid platform token");
        }
    }

    private byte[] sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign platform token", e);
        }
    }

    private String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    public record IssuedPlatformToken(String token, long expiresInSeconds, Instant expiresAt) {
    }
}
