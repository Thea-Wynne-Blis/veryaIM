package com.veryal.application.group;

import java.time.Instant;

/**
 * 群成员视图：角色、在线状态与禁言状态，供群管理界面与成员列表一次取回。
 *
 * @param role             OWNER / ADMIN / MEMBER
 * @param mutedUntil       限时禁言的到期时间；永久禁言与未禁言都为 {@code null}
 * @param mutedPermanently 是否永久禁言
 */
public record GroupMemberView(long userId, String nickname, String avatar, boolean online,
                              String role, Instant mutedUntil, boolean mutedPermanently) {
}
