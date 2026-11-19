package com.veryal.application.moment;

/**
 * 动态作者 / 点赞人 / 评论人视图。好友备注优先于昵称展示。
 */
public record MomentAuthorView(long userId, String nickname, String avatar, String remark) {
}
