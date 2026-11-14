package com.veryal.access.core;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * Channel 属性键。
 */
public final class ChannelAttributes {

    public static final AttributeKey<Connection> CONNECTION = AttributeKey.valueOf("veryal.connection");

    public static final AttributeKey<Boolean> AUTHENTICATED = AttributeKey.valueOf("veryal.authenticated");

    private ChannelAttributes() {
    }

    public static Connection connection(Channel channel) {
        return channel.attr(CONNECTION).get();
    }
}
