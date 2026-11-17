package com.veryal.access.core.push;

import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.domain.delivery.RealtimeSessionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link RealtimeSessionPort} 的本地实现：只在当前实例的连接注册表里查找并关闭。
 *
 * <p>关闭时先给文本客户端下发一条 {@code session_closed} 提示，再发 WebSocket 关闭帧
 * （{@link Connection#CLOSE_SESSION_TERMINATED}），客户端据此停止自动重连。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalRealtimeSession implements RealtimeSessionPort {

    private final ConnectionRegistry connectionRegistry;

    @Override
    public int closeByTokenId(String tokenId, String reason) {
        if (tokenId == null || tokenId.isBlank()) {
            return 0;
        }
        List<Connection> targets = new ArrayList<>();
        for (Connection connection : connectionRegistry.findAll()) {
            if (connection.authenticated() && Objects.equals(tokenId, connection.tokenId())) {
                targets.add(connection);
            }
        }
        return closeAll(targets, reason);
    }

    @Override
    public int closeByDevice(long userId, String deviceId, String reason) {
        if (deviceId == null || deviceId.isBlank()) {
            return 0;
        }
        List<Connection> targets = new ArrayList<>();
        for (Connection connection : connectionRegistry.findByUserId(userId)) {
            if (connection.authenticated() && deviceId.equals(connection.deviceId())) {
                targets.add(connection);
            }
        }
        return closeAll(targets, reason);
    }

    @Override
    public int closeByUserId(long userId, String reason) {
        List<Connection> targets = new ArrayList<>();
        for (Connection connection : connectionRegistry.findByUserId(userId)) {
            if (connection.authenticated()) {
                targets.add(connection);
            }
        }
        return closeAll(targets, reason);
    }

    private int closeAll(List<Connection> targets, String reason) {
        int closed = 0;
        for (Connection connection : targets) {
            connection.closeWithReason(Connection.CLOSE_SESSION_TERMINATED, reason);
            closed++;
            log.info("realtime session closed: userId={}, deviceId={}, connectionId={}, reason={}",
                    connection.userId(), connection.deviceId(), connection.connectionId(), reason);
        }
        return closed;
    }
}
