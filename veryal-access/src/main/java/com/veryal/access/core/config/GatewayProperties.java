package com.veryal.access.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gateway 通用配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.gateway")
public class GatewayProperties {

    /** 当前节点标识，写入在线状态与日志。 */
    private String id = "gateway-local";

    /** 最大协议帧长度（字节）。 */
    private int maxFrameLength = 1024 * 1024;

    /** 读空闲超时：超过该时间没有收到任何帧则断开。 */
    private Duration heartbeatIdle = Duration.ofSeconds(90);

    /** 连接建立后必须在该时间内完成登录。 */
    private Duration loginTimeout = Duration.ofSeconds(10);

    /** 业务线程数。 */
    private int businessThreads = 16;

    /** 业务队列容量，必须有界。 */
    private int businessQueueCapacity = 10_000;
}
