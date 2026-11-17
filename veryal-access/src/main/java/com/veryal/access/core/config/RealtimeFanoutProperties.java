package com.veryal.access.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实时扇出配置。
 *
 * <p>消息落库提交后，本节点的推送不再占用发送请求线程，而是交给有界线程池执行：
 * 发送接口立即返回，队列有界且满了丢最旧任务，避免内存无界增长。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.realtime.fanout")
public class RealtimeFanoutProperties {

    /** 扇出线程数；0 表示不使用线程池，退回发送线程内同步扇出。 */
    private int threads = 2;

    /** 扇出队列容量，必须有界；队列满时丢弃最旧任务。 */
    private int queueCapacity = 10_000;
}
