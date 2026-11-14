package com.veryal.application.sync;

import com.veryal.common.core.CursorPage;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.conversation.Conversation;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 离线消息同步应用服务。
 *
 * <p>客户端上传各会话游标，服务端按 {@code seq > cursor} 返回缺失消息，客户端确认后再推进游标。</p>
 */
@Service
public class SyncApplicationService {

    private static final int MAX_SYNC_LIMIT = 200;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public SyncApplicationService(MessageRepository messageRepository,
                                  ConversationRepository conversationRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    @Transactional(readOnly = true)
    public CursorPage<Message> syncMessages(long tenantId, long userId, String conversationId,
                                            long cursor, int limit) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .filter(item -> item.tenantId() == tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        conversationRepository.findMember(conversation.id(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));

        int pageSize = limit <= 0 ? 100 : Math.min(limit, MAX_SYNC_LIMIT);
        List<Message> messages = messageRepository.pageAfter(tenantId, conversationId, Math.max(cursor, 0), pageSize + 1);
        boolean hasMore = messages.size() > pageSize;
        if (hasMore) {
            messages = messages.subList(0, pageSize);
        }
        long nextCursor = messages.isEmpty() ? Math.max(cursor, 0) : messages.get(messages.size() - 1).seq();
        return new CursorPage<>(messages, Long.toString(nextCursor), hasMore);
    }
}
