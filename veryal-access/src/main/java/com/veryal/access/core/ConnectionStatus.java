package com.veryal.access.core;

/**
 * 连接状态机。
 */
public enum ConnectionStatus {

    CONNECTED,
    AUTHENTICATING,
    ONLINE,
    RECONNECTING,
    CLOSED
}
