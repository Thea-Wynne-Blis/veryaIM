package com.veryal.application.push;

import com.veryal.domain.friend.FriendEvent;
import com.veryal.domain.push.OfflinePushMessage;
import com.veryal.domain.push.OfflinePushProvider;
import com.veryal.domain.push.PushDevice;
import com.veryal.domain.push.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 好友离线推送：按用户启用的设备 token 逐个投递。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflinePushService {

    private final OfflinePushProvider provider;
    private final PushDeviceRepository pushDeviceRepository;

    public void push(long userId, FriendEvent event) {
        List<PushDevice> devices = pushDeviceRepository.listEnabled(userId);
        if (devices.isEmpty()) {
            return;
        }
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
        Map<String, String> data = Map.of(
                "type", "FRIEND",
                "eventType", Integer.toString(event.type().code()),
                "fromUserId", Long.toString(event.fromUserId()),
                "toUserId", Long.toString(event.toUserId()),
                "requestId", Long.toString(event.requestId()));
        for (PushDevice device : devices) {
            try {
                provider.push(new OfflinePushMessage(userId, device.deviceId(), device.platform(),
                        device.token(), title, body, data));
            } catch (RuntimeException e) {
                log.warn("offline friend push failed: userId={}, deviceId={}", userId,
                        device.deviceId(), e);
            }
        }
    }
}
