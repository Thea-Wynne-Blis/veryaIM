package com.veryal.access.core.push;

import com.veryal.application.message.SendMessageResult;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 单机直投模式下的发送后通知入口。
 *
 * <p>HTTP 与 TCP/WebSocket 两条发送链路共用：MQ 关闭时立即推送本节点连接与 SSE 订阅，
 * MQ 开启时由 Outbox → RabbitMQ 消费者统一投递，避免重复推送。</p>
 */
@Component
public class SendMessageNotifier {

    private final RealtimeDeliveryPort realtimeDeliveryPort;
    private final boolean mqEnabled;

    public SendMessageNotifier(RealtimeDeliveryPort realtimeDeliveryPort,
                               @Value("${veryal.mq.enabled:false}") boolean mqEnabled) {
        this.realtimeDeliveryPort = realtimeDeliveryPort;
        this.mqEnabled = mqEnabled;
    }

    public void notify(SendMessageResult result) {
        if (!mqEnabled && !result.duplicate() && result.event() != null) {
            realtimeDeliveryPort.deliver(result.event());
        }
    }
}
