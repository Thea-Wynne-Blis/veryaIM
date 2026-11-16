package com.veryal.infrastructure.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 跨实例推送总线拓扑。
 *
 * <p>所有实例绑定同一个 fanout 交换机，每个实例一条非持久、独占、自动删除的队列
 * （队列名带实例标识）。实例下线后队列自动清理，不会留下空队列堆积。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class RealtimeBusConfig {

    public static final String PUSH_EXCHANGE = "veryal.push.exchange";

    /** 实例标识：主机名 + 端口 + 随机串，保证同机多实例也不会抢同一条队列。 */
    public record InstanceId(String value) {
    }

    @Bean
    public FanoutExchange realtimePushExchange() {
        return new FanoutExchange(PUSH_EXCHANGE, false, true);
    }

    @Bean
    public InstanceId realtimeInstanceId(Environment environment) {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "node";
        }
        String port = environment.getProperty("server.port", "8080");
        String suffix = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x100000));
        return new InstanceId(host + "-" + port + "-" + suffix);
    }

    @Bean
    public Queue realtimePushQueue(InstanceId instanceId) {
        return QueueBuilder.nonDurable("veryal.push." + instanceId.value())
                .exclusive()
                .autoDelete()
                .build();
    }

    @Bean
    public Binding realtimePushBinding(Queue realtimePushQueue, FanoutExchange realtimePushExchange) {
        return BindingBuilder.bind(realtimePushQueue).to(realtimePushExchange);
    }
}
