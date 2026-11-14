package com.veryal.access.tcp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TCP 接入配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.gateway.tcp")
public class TcpGatewayProperties {

    private boolean enabled = true;

    /** 0 表示由操作系统分配随机端口（测试使用）。 */
    private int port = 9099;

    private int bossThreads = 1;

    /** 0 表示使用 Netty 默认值（2 * CPU）。 */
    private int workerThreads = 0;

    private int backlog = 1024;
}
