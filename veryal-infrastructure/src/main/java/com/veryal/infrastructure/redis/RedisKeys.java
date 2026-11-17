package com.veryal.infrastructure.redis;

/**
 * Redis Key 统一管理，禁止在业务代码中散落 Key 字面量。
 */
public final class RedisKeys {

    private static final String PREFIX = "veryal:";
    private static final String PRESENCE = PREFIX + "presence:user:";
    private static final String CONNECTION = PREFIX + "connection:";
    private static final String GATEWAY = PREFIX + "gateway:";
    private static final String WS_TICKET = PREFIX + "ws:ticket:";

    private RedisKeys() {
    }

    public static String presence(long userId) {
        return PRESENCE + userId;
    }

    public static String connection(String connectionId) {
        return CONNECTION + connectionId;
    }

    public static String gateway(String gatewayId) {
        return GATEWAY + gatewayId;
    }

    public static String wsTicket(String ticket) {
        return WS_TICKET + ticket;
    }
}
