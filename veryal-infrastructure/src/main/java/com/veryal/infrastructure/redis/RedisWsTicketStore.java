package com.veryal.infrastructure.redis;

import com.veryal.common.json.JsonUtils;
import com.veryal.domain.identity.WsTicketPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis 一次性票据实现：{@code GETDEL} 保证原子消费，多实例部署下同样只能使用一次。
 */
@Repository
@ConditionalOnProperty(prefix = "veryal.redis", name = "enabled", havingValue = "true")
public class RedisWsTicketStore implements WsTicketPort {

    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public RedisWsTicketStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String issue(TicketPayload payload) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(RedisKeys.wsTicket(ticket), JsonUtils.toJson(payload), TICKET_TTL);
        return ticket;
    }

    @Override
    public TicketPayload consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String value = redisTemplate.opsForValue().getAndDelete(RedisKeys.wsTicket(ticket));
        if (value == null || value.isBlank()) {
            return null;
        }
        return JsonUtils.fromJson(value, TicketPayload.class);
    }

    @Override
    public long ttlSeconds() {
        return TICKET_TTL.toSeconds();
    }
}
