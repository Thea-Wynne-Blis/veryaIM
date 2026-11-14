package com.veryal.application.message.strategy;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.message.MessageType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 消息类型策略工厂：启动时注册全部策略，运行时按类型选择。
 */
@Component
public class MessageTypeStrategyFactory {

    private final Map<MessageType, MessageTypeStrategy> strategies;

    public MessageTypeStrategyFactory(List<MessageTypeStrategy> strategyList) {
        Map<MessageType, MessageTypeStrategy> map = new EnumMap<>(MessageType.class);
        for (MessageTypeStrategy strategy : strategyList) {
            if (map.put(strategy.type(), strategy) != null) {
                throw new IllegalStateException("duplicate message strategy: " + strategy.type());
            }
        }
        this.strategies = Map.copyOf(map);
    }

    public MessageTypeStrategy get(MessageType type) {
        MessageTypeStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MESSAGE_TYPE,
                    "no strategy for message type: " + type);
        }
        return strategy;
    }

    public void validate(MessageContentContext context) {
        get(context.type()).validate(context);
    }
}
