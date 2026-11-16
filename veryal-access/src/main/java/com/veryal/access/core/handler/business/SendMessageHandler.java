package com.veryal.access.core.handler.business;

import com.veryal.access.core.Connection;
import com.veryal.access.core.protocol.CommandContext;
import com.veryal.access.core.protocol.CommandHandler;
import com.veryal.access.core.push.SendMessageNotifier;
import com.veryal.application.message.MessageApplicationService;
import com.veryal.application.message.SendMessageCommand;
import com.veryal.application.message.SendMessageResult;
import com.veryal.domain.message.MessageType;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.protobuf.SendMessageRequest;
import com.veryal.protocol.protobuf.ServerAck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 发送消息命令：持久化成功后返回 Server ACK。
 *
 * <p>实时推送统一交给 {@link SendMessageNotifier}：MQ 关闭时在当前节点直推，
 * MQ 开启时由 Outbox → RabbitMQ → 消费者投递，避免同一条消息被重复实时推送。</p>
 */
@Component
@RequiredArgsConstructor
public class SendMessageHandler implements CommandHandler {

    private final MessageApplicationService messageApplicationService;
    private final SendMessageNotifier sendMessageNotifier;

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
                connection.userId(), connection.deviceId(),
                request.getConversationId(), request.getClientMessageId(), messageType,
                request.getContent().toByteArray()));

        ServerAck ack = ServerAck.newBuilder()
                .setClientMessageId(result.message().clientMsgId())
                .setServerMessageId(Long.toString(result.message().id()))
                .setConversationSequence(result.message().seq())
                .setStatus(0)
                .build();
        context.reply(Command.SERVER_ACK, ack);

        sendMessageNotifier.notify(result);
    }
}
