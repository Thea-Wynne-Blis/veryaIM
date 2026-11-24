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

    /**
     * 监听地址。留空表示所有网卡。
     *
     * <p>裸机部署建议 {@code 127.0.0.1}：网关只经 nginx 暴露，
     * 容器部署则必须留空（容器内绑回环会让端口映射连不上）。</p>
     */
    private String bindAddress = "";

    private String path = "/ws";

    private int bossThreads = 1;

    private int workerThreads = 0;

    private int backlog = 1024;

    /** HTTP 聚合器上限。 */
    private int maxHttpContentLength = 64 * 1024;
}
