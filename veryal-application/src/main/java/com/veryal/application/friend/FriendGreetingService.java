package com.veryal.application.friend;

import com.veryal.application.conversation.ConversationApplicationService;
import com.veryal.application.conversation.ConversationView;
import com.veryal.application.message.MessageApplicationService;
import com.veryal.application.message.SendMessageCommand;
import com.veryal.common.core.RandomIds;
import com.veryal.domain.message.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * 成为好友后的自动打招呼。
 *
 * <p>通过好友申请时，接受方自动给申请方发一条文本消息。这样双方会话列表
 * 会立刻出现这个会话（实时推送到达后客户端拉一次卡片），不需要刷新页面。
 *
 * <p>用独立事务执行：打招呼失败（限流、内容校验等）不能回滚好友关系，
 * 调用方捕获异常即可。
 */
@Service
@RequiredArgsConstructor
public class FriendGreetingService {

    /** 自动发送的第一条消息。 */
    public static final String GREETING_TEXT = "我已经通过你的聊天申请，让我开始聊天吧！";

    private final ConversationApplicationService conversationApplicationService;
    private final MessageApplicationService messageApplicationService;

    /**
     * @param accepterId  通过申请的一方（消息发送者）
     * @param requesterId 发起申请的一方（消息接收者）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void greet(long accepterId, long requesterId) {
        if (accepterId == requesterId) {
            return;
        }
        ConversationView conversation = conversationApplicationService
                .createPrivate(accepterId, requesterId);
        messageApplicationService.send(new SendMessageCommand(accepterId, null,
                conversation.conversationId(), "greet-" + RandomIds.newId(),
                MessageType.TEXT, GREETING_TEXT.getBytes(StandardCharsets.UTF_8)));
    }
}
