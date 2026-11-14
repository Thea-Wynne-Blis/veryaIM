package com.veryal.access.core;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 单机连接注册表实现。
 *
 * <p>一个用户可以有多个连接（多设备 / 多标签页）。集群部署时该注册表由 Redis 路由 + MQ 投递扩展。</p>
 */
@Component
public class LocalConnectionRegistry implements ConnectionRegistry {

    private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, List<Connection>> byUser = new ConcurrentHashMap<>();

    @Override
    public void register(Connection connection) {
        connections.put(connection.connectionId(), connection);
        if (connection.userId() > 0) {
            byUser.computeIfAbsent(connection.userId(), id -> new CopyOnWriteArrayList<>()).add(connection);
        }
    }

    /**
     * 登录成功后调用，将连接挂到用户索引。
     */
    public void bindUser(Connection connection) {
        List<Connection> list = byUser.computeIfAbsent(connection.userId(), id -> new CopyOnWriteArrayList<>());
        if (!list.contains(connection)) {
            list.add(connection);
        }
    }

    @Override
    public void unregister(String connectionId) {
        Connection removed = connections.remove(connectionId);
        if (removed == null) {
            return;
        }
        removed.markClosed();
        if (removed.userId() > 0) {
            List<Connection> list = byUser.get(removed.userId());
            if (list != null) {
                list.remove(removed);
                if (list.isEmpty()) {
                    byUser.remove(removed.userId(), list);
                }
            }
        }
    }

    @Override
    public Connection find(String connectionId) {
        return connections.get(connectionId);
    }

    @Override
    public List<Connection> findByUserId(long userId) {
        List<Connection> list = byUser.get(userId);
        return list == null ? List.of() : List.copyOf(list);
    }

    @Override
    public Collection<Connection> findAll() {
        return List.copyOf(connections.values());
    }

    @Override
    public int activeCount() {
        return connections.size();
    }

    Map<Long, List<Connection>> userIndex() {
        return byUser;
    }
}
