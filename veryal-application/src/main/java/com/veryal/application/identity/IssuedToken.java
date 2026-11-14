package com.veryal.application.identity;

import java.time.Instant;

/**
 * 签发结果。
 */
public record IssuedToken(String token, Instant expiresAt) {
}
