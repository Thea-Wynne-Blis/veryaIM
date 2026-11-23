package com.veryal.domain.identity;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;

import java.time.Instant;

/**
 * 用户聚合根。
 *
 * <p>不依赖 Spring / Netty / MyBatis，方法只返回新对象。</p>
 */
public record User(long id, String username, String nickname, String avatar,
                   String region, String signature,
                   UserStatus status, String passwordHash, Instant createdAt, Instant updatedAt) {

    /** 地区最大长度，和 im_user.region 保持一致。 */
    public static final int MAX_REGION_LENGTH = 64;
    /** 个性签名最大长度，和 im_user.signature 保持一致。 */
    public static final int MAX_SIGNATURE_LENGTH = 255;

    public User {
        if (id <= 0) {
            throw new IllegalArgumentException("user id must be positive");
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

    public static User register(long id, String username, String nickname,
                                String passwordHash, Instant now) {
        return new User(id, username, nickname, null, null, null,
                UserStatus.ACTIVE, passwordHash, now, now);
    }

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }

    public void ensureActive() {
        if (!active()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "user is disabled: " + id);
        }
    }

    /**
     * 更新个人资料。
     *
     * <p>传 null 表示该字段不变；传空串表示清空（地区、签名）。昵称不允许清空。</p>
     */
    public User updateProfile(String nickname, String avatar, String region, String signature,
                              Instant now) {
        String newNickname = (nickname == null || nickname.isBlank()) ? this.nickname : nickname;
        String newAvatar = avatar == null ? this.avatar : avatar;
        String newRegion = region == null ? this.region : region;
        String newSignature = signature == null ? this.signature : signature;
        if (newRegion != null && newRegion.length() > MAX_REGION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "region too long");
        }
        if (newSignature != null && newSignature.length() > MAX_SIGNATURE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "signature too long");
        }
        return new User(id, username, newNickname, newAvatar, newRegion, newSignature,
                status, passwordHash, createdAt, now);
    }

    public User withStatus(UserStatus newStatus, Instant now) {
        return new User(id, username, nickname, avatar, region, signature, newStatus, passwordHash,
                createdAt, now);
    }

    /** 只替换头像地址；{@code newAvatar} 传 null 表示清除头像。 */
    public User withAvatar(String newAvatar, Instant now) {
        return new User(id, username, nickname, newAvatar, region, signature, status, passwordHash,
                createdAt, now);
    }
}
