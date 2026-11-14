package com.veryal.infrastructure.mq;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox 发布配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.outbox")
public class OutboxProperties {

    /** 单轮最多发布条数。 */
    private int batchSize = 100;

    /** 轮询间隔（毫秒）。 */
    private long pollIntervalMs = 500;

    /** 最大重试次数，超过后进入失败状态等待人工补偿。 */
    private int maxRetries = 5;

    /** 等待 Publisher Confirm 的超时时间（毫秒）。 */
    private long confirmTimeoutMs = 5_000;

    /** PUBLISHING 状态超过该时间视为节点故障，回收为待重试。 */
    private long staleTimeoutMs = 60_000;
}
