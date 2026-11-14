package com.veryal.access.websocket;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebSocket 接入配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.gateway.websocket")
public class WebSocketGatewayProperties {

    private boolean enabled = true;

    /** 0 表示由操作系统分配随机端口（测试使用）。 */
    private int port = 9098;

    private String path = "/ws";

    private int bossThreads = 1;

    private int workerThreads = 0;

    private int backlog = 1024;

    /** HTTP 聚合器上限。 */
    private int maxHttpContentLength = 64 * 1024;
}
