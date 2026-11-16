package com.veryal.access.core.push;

import com.veryal.application.message.SendMessageResult;
import com.veryal.domain.delivery.RealtimeBusPort;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 发送后的实时通知入口。
 *
 * <p>HTTP 与 TCP/WebSocket 两条发送链路共用。</p>
 *
 * <p>投递顺序：先在本节点直推（保证同机在线用户零延迟），再通过
 * {@link RealtimeBusPort} 广播给其他实例（MQ 开启时才有实现）。
 * 消费端与本地直推会收到同一 eventId，由投递实现去重，不会重复推送。</p>
 */
@Component
public class SendMessageNotifier {

    private final RealtimeDeliveryPort realtimeDeliveryPort;
    private final ObjectProvider<RealtimeBusPort> realtimeBusPort;

    public SendMessageNotifier(RealtimeDeliveryPort realtimeDeliveryPort,
                               ObjectProvider<RealtimeBusPort> realtimeBusPort) {
        this.realtimeDeliveryPort = realtimeDeliveryPort;
        this.realtimeBusPort = realtimeBusPort;
    }

    public void notify(SendMessageResult result) {
        if (result.duplicate() || result.event() == null) {
            return;
        }
        realtimeDeliveryPort.deliver(result.event());
        realtimeBusPort.ifAvailable(bus -> bus.publish(result.event()));
    }
}
