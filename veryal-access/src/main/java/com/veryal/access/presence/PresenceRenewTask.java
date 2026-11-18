package com.veryal.access.presence;

import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.access.core.config.GatewayProperties;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.identity.SessionApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 在线状态续期。
 *
 * <p>浏览器连接只发应用层 ping，过去只更新本地心跳，Redis 里的 presence 5 分钟 TTL
 * 到期后用户仍连着却显示离线。这里按固定间隔把本机已认证连接的在线状态续期，
 * 既避免在 Netty EventLoop 上做阻塞 Redis 调用，也不依赖客户端心跳频率。</p>
 *
 * <p>多网关部署时每个实例只续期自己的连接；已断开或换号的连接会被
 * {@code SessionApplicationService.renew} 里的 connectionId 校验拦住。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceRenewTask {

    private final ConnectionRegistry connectionRegistry;
    private final SessionApplicationService sessionService;
    private final GatewayProperties gatewayProperties;

    @Scheduled(fixedDelayString = "${veryal.presence.renew-interval-ms:60000}",
            initialDelayString = "${veryal.presence.renew-interval-ms:60000}")
    public void renew() {
        Collection<Connection> connections = connectionRegistry.findAll();
        int renewed = 0;
        for (Connection connection : connections) {
            if (!connection.authenticated() || !connection.isActive()) {
                continue;
            }
            try {
                if (sessionService.renew(new AuthenticatedUser(connection.userId(),
                        connection.deviceId(), connection.tokenId()),
                        gatewayProperties.getId(), connection.connectionId())) {
                    renewed++;
                }
            } catch (RuntimeException e) {
                log.warn("presence renew failed: connectionId={}, userId={}",
                        connection.connectionId(), connection.userId(), e);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("presence renew: connections={}, renewed={}", connections.size(), renewed);
        }
    }
}
