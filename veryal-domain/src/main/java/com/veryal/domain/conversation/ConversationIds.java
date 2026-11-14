package com.veryal.domain.conversation;

/**
 * 会话 ID 生成规则。
 *
 * <p>单聊 ID 由租户与两个用户 ID 确定性生成，保证同一对用户只有一个单聊会话，
 * 同时天然具备幂等性；群聊 ID 使用雪花 ID。</p>
 */
public final class ConversationIds {

    private static final String PRIVATE_PREFIX = "p_";
    private static final String GROUP_PREFIX = "g_";

    private ConversationIds() {
    }

    public static String privateId(long tenantId, long userA, long userB) {
        long first = Math.min(userA, userB);
        long second = Math.max(userA, userB);
        return PRIVATE_PREFIX + tenantId + "_" + first + "_" + second;
    }

    public static String groupId(long tenantId, long snowflakeId) {
        return GROUP_PREFIX + tenantId + "_" + snowflakeId;
    }

    public static boolean isPrivate(String conversationId) {
        return conversationId != null && conversationId.startsWith(PRIVATE_PREFIX);
    }
}
