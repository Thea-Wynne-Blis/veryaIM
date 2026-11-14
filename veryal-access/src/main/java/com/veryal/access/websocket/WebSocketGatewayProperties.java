package com.veryal.access.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebSocket 接入配置。
 */
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public int getMaxHttpContentLength() {
        return maxHttpContentLength;
    }

    public void setMaxHttpContentLength(int maxHttpContentLength) {
        this.maxHttpContentLength = maxHttpContentLength;
    }
}
