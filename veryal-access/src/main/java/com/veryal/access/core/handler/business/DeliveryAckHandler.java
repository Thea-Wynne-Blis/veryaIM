package com.veryal.access.core.handler.business;

import com.veryal.access.core.Connection;
import com.veryal.access.core.protocol.CommandContext;
import com.veryal.access.core.protocol.CommandHandler;
import com.veryal.application.conversation.ConversationApplicationService;
import com.veryal.application.message.MessageApplicationService;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.protobuf.DeliveryAck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 投递回执：记录消息已到达设备，并推进同步游标。
 */
@Component
@RequiredArgsConstructor
public class DeliveryAckHandler implements CommandHandler {

    private final MessageApplicationService messageApplicationService;
    private final ConversationApplicationService conversationApplicationService;

    @Override
    public Command command() {
        return Command.DELIVERY_ACK;
    }

    @Override
    public void handle(CommandContext context) {
        Connection connection = context.connection();
        DeliveryAck ack = context.payload(DeliveryAck.parser());
        long messageId;
        try {
            messageId = Long.parseLong(ack.getMessageId());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid message id: " + ack.getMessageId());
        }
        String conversationId = messageApplicationService.acknowledgeDelivery(connection.tenantId(),
                connection.userId(), messageId, ack.getDeviceId(), ack.getDeliveredSequence());
        conversationApplicationService.advanceSyncCursor(connection.tenantId(), connection.userId(),
                conversationId, ack.getDeliveredSequence());
    }
}
