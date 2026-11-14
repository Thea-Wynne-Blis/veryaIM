package com.veryal.application.identity;

import com.veryal.common.exception.AuthenticationException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import com.veryal.common.core.TimeProvider;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * JWT（HS256）实现。不引入第三方 JWT 依赖，保持协议层可控。
 *
 * <p>Token 载荷只包含身份路由所需的最小信息，不包含权限与敏感数据。</p>
 */
@Component
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final long CLOCK_SKEW_SECONDS = 30L;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final TokenProperties properties;
    private final TimeProvider timeProvider;

    public JwtTokenService(TokenProperties properties, TimeProvider timeProvider) {
        this.properties = properties;
        this.timeProvider = timeProvider;
    }

    public IssuedToken issue(long userId, long tenantId, String deviceId) {
        Instant now = timeProvider.now();
        Instant expiresAt = now.plus(properties.ttl());
        String payload = JsonUtils.toJson(Map.of(
                "sub", Long.toString(userId),
                "tid", tenantId,
                "did", deviceId == null ? "" : deviceId,
                "iss", properties.issuer(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond(),
                "jti", UUID.randomUUID().toString()
        ));
        String header = encode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String body = encode(payload.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + body;
        String token = signingInput + "." + encode(sign(signingInput));
        return new IssuedToken(token, expiresAt);
    }

    public AuthenticatedUser parse(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException(ErrorCode.UNAUTHORIZED, "token is missing");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "malformed token");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = sign(signingInput);
        byte[] actual;
        try {
            actual = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "malformed token signature", e);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "invalid token signature");
        }

        TokenClaims claims = readClaims(parts[1]);
        long now = timeProvider.now().getEpochSecond();
        if (claims.exp() + CLOCK_SKEW_SECONDS < now) {
            throw new AuthenticationException(ErrorCode.TOKEN_EXPIRED, "token expired");
        }
        try {
            return new AuthenticatedUser(Long.parseLong(claims.sub()), claims.tid(), claims.did(), claims.jti());
        } catch (NumberFormatException e) {
            throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "invalid token subject", e);
        }
    }

    private TokenClaims readClaims(String encodedPayload) {
        try {
            byte[] payload = DECODER.decode(encodedPayload);
            return JsonUtils.mapper().readValue(payload, TokenClaims.class);
        } catch (Exception e) {
            throw new AuthenticationException(ErrorCode.TOKEN_INVALID, "malformed token payload", e);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    /**
     * JWT 载荷。字段名遵循 JWT 注册声明。
     */
    record TokenClaims(String sub, long tid, String did, String iss, long iat, long exp, String jti) {
    }
}
