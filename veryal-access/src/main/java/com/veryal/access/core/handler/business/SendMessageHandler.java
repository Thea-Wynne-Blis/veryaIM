package com.veryal.access.core.handler.business;

import com.veryal.access.core.Connection;
import com.veryal.access.core.protocol.CommandContext;
import com.veryal.access.core.protocol.CommandHandler;
import com.veryal.access.core.push.MessagePushService;
import com.veryal.application.message.MessageApplicationService;
import com.veryal.application.message.SendMessageCommand;
import com.veryal.application.message.SendMessageResult;
import com.veryal.domain.message.MessageType;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.protobuf.SendMessageRequest;
import com.veryal.protocol.protobuf.ServerAck;
import org.springframework.stereotype.Component;

/**
 * 发送消息命令：持久化成功后返回 Server ACK，再异步实时推送。
 */
@Component
public class SendMessageHandler implements CommandHandler {

    private final MessageApplicationService messageApplicationService;
    private final MessagePushService messagePushService;

    public SendMessageHandler(MessageApplicationService messageApplicationService,
                              MessagePushService messagePushService) {
        this.messageApplicationService = messageApplicationService;
        this.messagePushService = messagePushService;
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
                connection.tenantId(), connection.userId(), request.getConversationId(),
                request.getClientMessageId(), messageType, request.getContent().toByteArray()));

        ServerAck ack = ServerAck.newBuilder()
                .setClientMessageId(result.message().clientMsgId())
                .setServerMessageId(Long.toString(result.message().id()))
                .setConversationSequence(result.message().seq())
                .setStatus(0)
                .build();
        context.reply(Command.SERVER_ACK, ack);
        messagePushService.push(result.message(), result.recipientUserIds(), connection);
    }
}
