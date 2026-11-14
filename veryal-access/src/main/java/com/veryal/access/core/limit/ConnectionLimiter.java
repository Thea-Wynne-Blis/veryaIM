package com.veryal.access.core.limit;

import com.veryal.access.core.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 连接数限制器：同时限制单节点总连接数与单 IP 连接数。
 *
 * <p>连接建立频率低，使用同步计数即可；0 表示不限制。</p>
 */
@Component
@RequiredArgsConstructor
public class ConnectionLimiter {

    private final GatewayProperties properties;
    private final Map<String, Integer> perIp = new HashMap<>();
    private int total;

    public synchronized boolean tryAcquire(String ip) {
        int maxTotal = properties.getMaxConnections();
        int maxPerIp = properties.getMaxConnectionsPerIp();
        if (maxTotal > 0 && total >= maxTotal) {
            return false;
        }
        if (maxPerIp > 0 && perIp.getOrDefault(ip, 0) >= maxPerIp) {
            return false;
        }
        total++;
        perIp.merge(ip, 1, Integer::sum);
        return true;
    }

    public synchronized void release(String ip) {
        if (total > 0) {
            total--;
        }
        perIp.computeIfPresent(ip, (key, value) -> value <= 1 ? null : value - 1);
    }

    public synchronized int activeCount() {
        return total;
    }
}
