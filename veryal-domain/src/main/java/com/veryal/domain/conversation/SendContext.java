package com.veryal.domain.conversation;

import java.time.Instant;

/**
 * 发送消息前需要校验的会话上下文。
 *
 * <p>把「会话类型、发送者是否为成员、发送者是否被全局禁言、私聊对端以及对端是否拉黑发送者」
 * 合并成一次查询取回：发送是该系统最热的写入路径，原来这些校验各自一次数据库往返，
 * 单条消息要为此多付 5 次 RTT。</p>
 *
 * @param type        会话类型
 * @param member      发送者是否属于该会话
 * @param mutedUntil  发送者全局禁言到期时间，{@code null} 表示未被禁言
 * @param peerUserId  私聊对端用户 ID；群聊或对端不存在时为 {@code null}
 * @param peerBlocked 私聊对端是否把发送者拉黑
 */
public record SendContext(ConversationType type, boolean member, Instant mutedUntil,
                          Long peerUserId, boolean peerBlocked) {
}
