package com.veryal.access.core.push.local;

import com.veryal.domain.friend.FriendEvent;
import com.veryal.domain.friend.FriendNotifier;
import com.veryal.domain.friend.FriendPushPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQ 关闭时的好友事件本机直推。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class LocalFriendNotifier implements FriendNotifier {

    private final FriendPushPort friendPushPort;

    @Override
    public void notify(FriendEvent event) {
        friendPushPort.push(event);
    }
}
