package com.veryal.access.core.push;

import com.veryal.application.message.SendMessageResult;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 发送后的实时通知入口。
 *
 * <p>HTTP 与 TCP/WebSocket 两条发送链路共用。</p>
 *
 * <p>无论 MQ 是否开启，都先在本节点立即投递（保证发送方与同机在线用户零延迟）；
 * MQ 开启时消费端还会再投递一次，由 {@link RealtimeDelivery} 按 eventId 去重，
 * 因此多实例下跨节点推送仍然有效，而本机延迟不受 500ms Outbox 轮询影响。</p>
 */
@Component
@RequiredArgsConstructor
public class SendMessageNotifier {

    private final RealtimeDeliveryPort realtimeDeliveryPort;

    public void notify(SendMessageResult result) {
        if (!result.duplicate() && result.event() != null) {
            realtimeDeliveryPort.deliver(result.event());
        }
    }
}
