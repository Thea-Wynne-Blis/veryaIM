package com.veryal.access.tcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TCP 接入配置。
 */
@ConfigurationProperties(prefix = "veryal.gateway.tcp")
public class TcpGatewayProperties {

    private boolean enabled = true;

    /** 0 表示由操作系统分配随机端口（测试使用）。 */
    private int port = 9099;

    private int bossThreads = 1;

    /** 0 表示使用 Netty 默认值（2 * CPU）。 */
    private int workerThreads = 0;

    private int backlog = 1024;

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
}
