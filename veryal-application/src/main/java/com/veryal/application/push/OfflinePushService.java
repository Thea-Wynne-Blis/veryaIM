package com.veryal.application.push;

import com.veryal.domain.friend.FriendEvent;
import com.veryal.domain.friend.FriendEventType;
import com.veryal.domain.push.OfflinePushMessage;
import com.veryal.domain.push.OfflinePushProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 好友离线推送：用户没有在线连接时交给 provider（APNs/FCM/厂商网关）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflinePushService {

    private final OfflinePushProvider provider;

    public void push(long userId, FriendEvent event) {
        try {
            String title = switch (event.type()) {
                case REQUEST -> "新的好友申请";
                case ACCEPTED -> "好友申请已通过";
                case REJECTED -> "好友申请被拒绝";
                case CANCELED -> "好友申请已取消";
                case BLOCKED -> "你已被拉黑";
                case UNBLOCKED -> "已被解除拉黑";
            };
            String body = event.message() == null || event.message().isBlank()
                    ? "打开应用查看详情" : event.message();
            provider.push(new OfflinePushMessage(userId, title, body, Map.of(
                    "type", "FRIEND",
                    "eventType", Integer.toString(event.type().code()),
                    "fromUserId", Long.toString(event.fromUserId()),
                    "toUserId", Long.toString(event.toUserId()),
                    "requestId", Long.toString(event.requestId()))));
        } catch (RuntimeException e) {
            log.warn("offline friend push failed: userId={}, type={}", userId, event.type(), e);
        }
    }
}
