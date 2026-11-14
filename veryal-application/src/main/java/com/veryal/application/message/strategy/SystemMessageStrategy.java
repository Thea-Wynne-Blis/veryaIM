package com.veryal.application.message.strategy;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Component;

/**
 * 系统消息：只允许服务端产生，客户端提交直接拒绝。
 */
@Component
public class SystemMessageStrategy implements MessageTypeStrategy {

    @Override
    public MessageType type() {
        return MessageType.SYSTEM;
    }

    @Override
    public void validate(MessageContentContext context) {
        throw new BusinessException(ErrorCode.UNSUPPORTED_MESSAGE_TYPE,
                "system message is reserved for server");
    }
}
