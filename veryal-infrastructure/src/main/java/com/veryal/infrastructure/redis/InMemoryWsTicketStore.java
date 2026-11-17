package com.veryal.infrastructure.redis;

import com.veryal.domain.identity.WsTicketPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存票据实现，用于本地开发与测试（{@code veryal.redis.enabled=false}）。
 *
 * <p>只在单实例下正确；多实例部署必须开启 Redis。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "veryal.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryWsTicketStore implements WsTicketPort {

    private static final Duration TICKET_TTL = Duration.ofSeconds(60);
    private static final int SWEEP_THRESHOLD = 1024;

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

    @Override
    public String issue(TicketPayload payload) {
        if (tickets.size() > SWEEP_THRESHOLD) {
            long now = System.currentTimeMillis();
            tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Entry(payload, System.currentTimeMillis() + TICKET_TTL.toMillis()));
        return ticket;
    }

    @Override
    public TicketPayload consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        Entry entry = tickets.remove(ticket);
        if (entry == null || entry.expiresAt() <= System.currentTimeMillis()) {
            return null;
        }
        return entry.payload();
    }

    @Override
    public long ttlSeconds() {
        return TICKET_TTL.toSeconds();
    }

    private record Entry(TicketPayload payload, long expiresAt) {
    }
}
