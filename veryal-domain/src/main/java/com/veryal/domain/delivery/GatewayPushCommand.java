package com.veryal.domain.delivery;

import com.veryal.domain.message.event.MessageCreatedEvent;

import java.util.List;

/**
 * 下发给单个 Gateway 的推送命令。
 *
 * <p>投递消费者按 Redis 路由把连接按 gatewayId 分组，每个 Gateway 只收到需要推送给
 * 本节点连接的 connectionId 列表。</p>
 */
public record GatewayPushCommand(String eventId, String gatewayId, List<String> connectionIds,
                                 MessageCreatedEvent message) {

    public GatewayPushCommand {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (gatewayId == null || gatewayId.isBlank()) {
            throw new IllegalArgumentException("gatewayId must not be blank");
        }
        if (connectionIds == null) {
            connectionIds = List.of();
        }
        connectionIds = List.copyOf(connectionIds);
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
    }
}
