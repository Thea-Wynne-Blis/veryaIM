package com.veryal.access.core.handler.business;

import com.veryal.access.core.Connection;
import com.veryal.access.core.protocol.CommandContext;
import com.veryal.access.core.protocol.CommandHandler;
import com.veryal.application.conversation.ConversationApplicationService;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.protobuf.ReadAck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 阅读回执：推进用户会话读游标（只前进）。
 */
@Component
@RequiredArgsConstructor
public class ReadAckHandler implements CommandHandler {

    private final ConversationApplicationService conversationApplicationService;

    @Override
    public Command command() {
        return Command.READ_ACK;
    }

    @Override
    public void handle(CommandContext context) {
        Connection connection = context.connection();
        ReadAck ack = context.payload(ReadAck.parser());
        conversationApplicationService.markRead(connection.tenantId(), connection.userId(),
                ack.getConversationId(), ack.getReadSequence());
    }
}
