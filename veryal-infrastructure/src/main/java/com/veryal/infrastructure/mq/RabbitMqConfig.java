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
 * <p>投递链路：消息事件交换机 → 全局单投递队列 → 路由消费者（单实例消费）；
 * 路由消费者按 Redis 连接路由把推送命令发到网关交换机 → 各 Gateway 自己的推送队列。
 * 失败消息进入 TTL 重试队列，超过上限进入共享死信队列。</p>
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
    public TopicExchange gatewayExchange() {
        return new TopicExchange(RabbitConstants.GATEWAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue messageDeliveryQueue() {
        return QueueBuilder.durable(RabbitConstants.DELIVERY_QUEUE)
                .deadLetterExchange(RabbitConstants.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitConstants.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue messageRetryQueue() {
        return QueueBuilder.durable(RabbitConstants.RETRY_QUEUE)
                .ttl(RabbitConstants.RETRY_TTL_MILLIS)
                .deadLetterExchange(RabbitConstants.MESSAGE_EXCHANGE)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(RabbitConstants.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Queue gatewayPushQueue(RabbitInstance instance) {
        return QueueBuilder.nonDurable(instance.gatewayPushQueueName())
                .autoDelete()
                .deadLetterExchange(RabbitConstants.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitConstants.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue readReceiptQueue(RabbitInstance instance) {
        return QueueBuilder.nonDurable(instance.readReceiptQueueName())
                .autoDelete()
                .build();
    }

    @Bean
    public Queue messageRecallQueue(RabbitInstance instance) {
        return QueueBuilder.nonDurable(instance.messageRecallQueueName())
                .autoDelete()
                .build();
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
    public Binding readReceiptBinding(TopicExchange messageExchange, Queue readReceiptQueue) {
        return BindingBuilder.bind(readReceiptQueue).to(messageExchange)
                .with(MessageEvents.READ_RECEIPT);
    }

    @Bean
    public Binding messageRecallBinding(TopicExchange messageExchange, Queue messageRecallQueue) {
        return BindingBuilder.bind(messageRecallQueue).to(messageExchange)
                .with(MessageEvents.MESSAGE_RECALLED);
    }

    @Bean
    public Binding gatewayPushBinding(TopicExchange gatewayExchange, Queue gatewayPushQueue,
                                      RabbitInstance instance) {
        return BindingBuilder.bind(gatewayPushQueue).to(gatewayExchange)
                .with(instance.gatewayPushRoutingKey());
    }

    @Bean
    public Binding deadLetterBinding(DirectExchange deadLetterExchange, Queue deadLetterQueue) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                .with(RabbitConstants.DEAD_ROUTING_KEY);
    }
}
