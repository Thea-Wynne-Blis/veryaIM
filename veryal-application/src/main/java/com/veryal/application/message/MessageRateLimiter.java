package com.veryal.application.message;

import com.veryal.common.ratelimit.RateLimit;
import org.springframework.stereotype.Component;

/**
 * 消息发送限流入口：放在幂等与内容校验之后，避免无效/重复消息消耗额度。
 */
@Component
public class MessageRateLimiter {

    @RateLimit(policy = "message-send", key = "#p0 + ':' + #p1")
    public void acquire(long tenantId, long senderId) {
        // 令牌由切面消费
    }
}
