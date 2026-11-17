package com.veryal.application.identity;

/**
 * 认证通过后的当前用户身份。WebSocket / HTTP 共用。
 */
public record AuthenticatedUser(long userId, String deviceId, String tokenId) {
}
