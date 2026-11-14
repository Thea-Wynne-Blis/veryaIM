package com.veryal.infrastructure.mq;

import com.veryal.domain.message.event.MessageEvents;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑。
 *
 * <p>主交换机保存消息事件；每个实例声明自己的临时投递队列并绑定 {@code message.*.created}，
 * 从而每个实例都能收到全部事件并只推送本节点连接。失败消息进入实例级 TTL 重试队列，
 * 超过重试上限进入共享死信队列。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    @Bean
    public TopicExchange messageExchange() {
        return new TopicExchange(RabbitConstants.MESSAGE_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange retryExchange() {
        return new TopicExchange(RabbitConstants.RETRY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(RabbitConstants.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue messageDeliveryQueue(RabbitInstance instance) {
        return QueueBuilder.nonDurable(instance.deliveryQueueName())
                .autoDelete()
                .deadLetterExchange(RabbitConstants.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitConstants.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue messageRetryQueue(RabbitInstance instance) {
        return QueueBuilder.nonDurable(instance.retryQueueName())
                .autoDelete()
                .ttl(RabbitConstants.RETRY_TTL_MILLIS)
                .deadLetterExchange(RabbitConstants.MESSAGE_EXCHANGE)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(RabbitConstants.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding privateCreatedBinding(TopicExchange messageExchange, Queue messageDeliveryQueue) {
        return BindingBuilder.bind(messageDeliveryQueue).to(messageExchange)
                .with(MessageEvents.PRIVATE_CREATED);
    }

    @Bean
    public Binding groupCreatedBinding(TopicExchange messageExchange, Queue messageDeliveryQueue) {
        return BindingBuilder.bind(messageDeliveryQueue).to(messageExchange)
                .with(MessageEvents.GROUP_CREATED);
    }

    @Bean
    public Binding retryPrivateBinding(TopicExchange retryExchange, Queue messageRetryQueue) {
        return BindingBuilder.bind(messageRetryQueue).to(retryExchange)
                .with(MessageEvents.PRIVATE_CREATED);
    }

    @Bean
    public Binding retryGroupBinding(TopicExchange retryExchange, Queue messageRetryQueue) {
        return BindingBuilder.bind(messageRetryQueue).to(retryExchange)
                .with(MessageEvents.GROUP_CREATED);
    }

    @Bean
    public Binding deadLetterBinding(DirectExchange deadLetterExchange, Queue deadLetterQueue) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                .with(RabbitConstants.DEAD_ROUTING_KEY);
    }
}
