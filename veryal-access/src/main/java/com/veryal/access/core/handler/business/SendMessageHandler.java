package com.veryal.access.core.handler.business;

import com.veryal.access.core.Connection;
import com.veryal.access.core.protocol.CommandContext;
import com.veryal.access.core.protocol.CommandHandler;
import com.veryal.application.message.MessageApplicationService;
import com.veryal.application.message.SendMessageCommand;
import com.veryal.application.message.SendMessageResult;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import com.veryal.domain.message.MessageType;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.protobuf.SendMessageRequest;
import com.veryal.protocol.protobuf.ServerAck;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 发送消息命令：持久化成功后返回 Server ACK。
 *
 * <p>MQ 关闭时在当前节点直接推送；MQ 开启时由 Outbox → RabbitMQ → 消费者统一投递，
 * 避免同一条消息被重复实时推送。</p>
 */
@Component
public class SendMessageHandler implements CommandHandler {

    private final MessageApplicationService messageApplicationService;
    private final RealtimeDeliveryPort realtimeDeliveryPort;
    private final boolean mqEnabled;

    public SendMessageHandler(MessageApplicationService messageApplicationService,
                              RealtimeDeliveryPort realtimeDeliveryPort,
                              @Value("${veryal.mq.enabled:false}") boolean mqEnabled) {
        this.messageApplicationService = messageApplicationService;
        this.realtimeDeliveryPort = realtimeDeliveryPort;
        this.mqEnabled = mqEnabled;
    }

    @Override
    public Command command() {
        return Command.SEND_MESSAGE;
    }

    @Override
    public void handle(CommandContext context) {
        Connection connection = context.connection();
        SendMessageRequest request = context.payload(SendMessageRequest.parser());
        MessageType messageType = MessageType.fromCode(request.getMessageType());
        SendMessageResult result = messageApplicationService.send(new SendMessageCommand(
                connection.tenantId(), connection.userId(), connection.deviceId(),
                request.getConversationId(), request.getClientMessageId(), messageType,
                request.getContent().toByteArray()));

        ServerAck ack = ServerAck.newBuilder()
                .setClientMessageId(result.message().clientMsgId())
                .setServerMessageId(Long.toString(result.message().id()))
                .setConversationSequence(result.message().seq())
                .setStatus(0)
                .build();
        context.reply(Command.SERVER_ACK, ack);

        if (!mqEnabled && !result.duplicate() && result.event() != null) {
            realtimeDeliveryPort.deliver(result.event());
        }
    }
}
