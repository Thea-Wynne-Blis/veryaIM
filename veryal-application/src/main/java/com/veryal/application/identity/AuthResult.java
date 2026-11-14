package com.veryal.application.identity;

/**
 * 认证结果。
 */
public record AuthResult(UserProfile user, String deviceId, String token, long expiresInSeconds) {
}
