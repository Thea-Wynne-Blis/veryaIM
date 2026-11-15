package com.veryal.domain.identity;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;

import java.time.Instant;

/**
 * 用户聚合根。
 *
 * <p>领域对象不依赖 Spring / Netty / MyBatis；密码只保存哈希。</p>
 */
public record User(long id, long tenantId, String username, String nickname, String avatar,
                   UserStatus status, String passwordHash, Instant createdAt, Instant updatedAt) {

    public User {
        if (id <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenant id must be positive");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (nickname == null || nickname.isBlank()) {
            nickname = username;
        }
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public static User register(long id, long tenantId, String username, String nickname,
                                String passwordHash, Instant now) {
        return new User(id, tenantId, username, nickname, null, UserStatus.ACTIVE, passwordHash, now, now);
    }

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }

    public void ensureActive() {
        if (!active()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "user is disabled: " + id);
        }
    }

    public User updateProfile(String nickname, String avatar, Instant now) {
        String newNickname = (nickname == null || nickname.isBlank()) ? this.nickname : nickname;
        String newAvatar = avatar == null ? this.avatar : avatar;
        return new User(id, tenantId, username, newNickname, newAvatar, status, passwordHash, createdAt, now);
    }

    public User withStatus(UserStatus newStatus, Instant now) {
        return new User(id, tenantId, username, nickname, avatar, newStatus, passwordHash,
                createdAt, now);
    }
}
