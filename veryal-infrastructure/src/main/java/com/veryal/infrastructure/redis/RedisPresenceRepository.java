package com.veryal.infrastructure.redis;

import com.veryal.common.json.JsonUtils;
import com.veryal.domain.presence.PresenceRecord;
import com.veryal.domain.presence.PresenceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Redis 在线状态实现：一个用户一个 Hash，field 为 deviceId，value 为连接元数据 JSON。
 *
 * <p>心跳续期、断开与踢下线都必须校验 connectionId，避免旧连接误删新连接。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "veryal.redis", name = "enabled", havingValue = "true")
public class RedisPresenceRepository implements PresenceRepository {

    private static final Duration PRESENCE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public RedisPresenceRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void markOnline(PresenceRecord record) {
        String key = RedisKeys.presence(record.userId());
        redisTemplate.opsForHash().put(key, record.deviceId(), JsonUtils.toJson(record));
        redisTemplate.expire(key, PRESENCE_TTL);
    }

    @Override
    public boolean heartbeat(long userId, String deviceId, String connectionId, Instant now) {
        String key = RedisKeys.presence(userId);
        PresenceRecord current = readRecord(key, deviceId);
        if (current == null || !current.connectionId().equals(connectionId)) {
            return false;
        }
        PresenceRecord renewed = new PresenceRecord(current.userId(), current.deviceId(),
                current.gatewayId(), current.connectionId(), now, current.status());
        redisTemplate.opsForHash().put(key, deviceId, JsonUtils.toJson(renewed));
        redisTemplate.expire(key, PRESENCE_TTL);
        return true;
    }

    @Override
    public void markOffline(long userId, String deviceId, String connectionId) {
        String key = RedisKeys.presence(userId);
        PresenceRecord current = readRecord(key, deviceId);
        if (current != null && current.connectionId().equals(connectionId)) {
            redisTemplate.opsForHash().delete(key, deviceId);
        }
    }

    @Override
    public List<PresenceRecord> findByUserId(long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RedisKeys.presence(userId));
        List<PresenceRecord> records = new ArrayList<>(entries.size());
        for (Object value : entries.values()) {
            records.add(JsonUtils.fromJson(Objects.toString(value), PresenceRecord.class));
        }
        return records;
    }

    @Override
    public boolean isOnline(long userId) {
        Long size = redisTemplate.opsForHash().size(RedisKeys.presence(userId));
        return size != null && size > 0;
    }

    /**
     * 用 pipeline 一次取回所有人的存在性：群成员列表最多 1000 人，
     * 逐人 EXISTS 会把一次接口放大成上千次 Redis 往返。
     */
    @Override
    public Set<Long> onlineUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = List.copyOf(userIds);
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : ids) {
                connection.keyCommands().exists(
                        RedisKeys.presence(id).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        Set<Long> online = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            Object value = results != null && i < results.size() ? results.get(i) : null;
            if (Boolean.TRUE.equals(value)) {
                online.add(ids.get(i));
            }
        }
        return online;
    }

    private PresenceRecord readRecord(String key, String deviceId) {
        Object value = redisTemplate.opsForHash().get(key, deviceId);
        if (value == null) {
            return null;
        }
        return JsonUtils.fromJson(Objects.toString(value), PresenceRecord.class);
    }
}
