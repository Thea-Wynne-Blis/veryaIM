package com.veryal.application.friend;

/**
 * 黑名单用户视图。
 */
public record BlockedUserView(long userId, String nickname, String avatar) {
}
