package com.veryal.access.core;

import java.util.Collection;
import java.util.List;

/**
 * 本地连接注册表。
 */
public interface ConnectionRegistry {

    void register(Connection connection);

    void unregister(String connectionId);

    Connection find(String connectionId);

    List<Connection> findByUserId(long userId);

    Collection<Connection> findAll();

    int activeCount();
}
