package com.veryal.infrastructure.mq;

import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.OutboxMapper;
import com.veryal.infrastructure.mysql.po.OutboxPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbox 发布器。
 *
 * <p>多实例安全：先用条件 UPDATE 抢占记录（status 0 → 4），抢占成功者负责发布；
 * Publisher Confirm 后置为已发布，失败按指数退避进入重试，超过上限进入失败状态。
 * 节点宕机遗留的 PUBLISHING 记录会被周期性回收。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class OutboxPublisher {

    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${veryal.outbox.poll-interval-ms:500}")
    public void publishPending() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            recoverStale();
            publishBatch();
        } catch (RuntimeException e) {
            log.error("outbox publish round failed", e);
        } finally {
            running.set(false);
        }
    }

    private void publishBatch() {
        Instant now = Instant.now();
        List<Long> pendingIds = outboxMapper.selectPendingIds(SqlTime.toLocal(now),
                Math.max(1, properties.getBatchSize()));
        for (Long id : pendingIds) {
            if (outboxMapper.claim(id, SqlTime.toLocal(Instant.now())) == 0) {
                continue;
            }
            OutboxPO record = outboxMapper.selectById(id);
            if (record == null) {
                continue;
            }
            publish(record);
        }
    }

    private void publish(OutboxPO record) {
        try {
            CorrelationData correlation = new CorrelationData(record.eventId);
            rabbitTemplate.convertAndSend(RabbitConstants.MESSAGE_EXCHANGE, record.eventType, record.payload,
                    message -> {
                        MessageProperties props = message.getMessageProperties();
                        props.setMessageId(record.eventId);
                        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        props.setContentEncoding("UTF-8");
                        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.getConfirmTimeoutMs(), TimeUnit.MILLISECONDS);
            ReturnedMessage returned = correlation.getReturned();
            if (returned != null) {
                // mandatory + publisher-returns：消息没有被任何队列接收（实例下线、绑定缺失）。
                // Broker 仍会回 ack，只看 confirm 会把它当成发布成功而永久丢事件。
                markRetry(record, "unroutable: exchange=" + returned.getExchange()
                        + ", routingKey=" + returned.getRoutingKey()
                        + ", reply=" + returned.getReplyText());
            } else if (confirm.isAck()) {
                outboxMapper.markPublished(record.id, SqlTime.toLocal(Instant.now()));
                log.debug("outbox published: eventId={}, type={}", record.eventId, record.eventType);
            } else {
                markRetry(record, "publisher nack: " + confirm.getReason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markRetry(record, "publish interrupted");
        } catch (Exception e) {
            markRetry(record, e.getMessage());
        }
    }

    private void markRetry(OutboxPO record, String reason) {
        Instant now = Instant.now();
        int attempts = record.retryCount + 1;
        if (attempts >= properties.getMaxRetries()) {
            outboxMapper.markFailed(record.id, SqlTime.toLocal(now), truncate(reason));
            log.error("outbox publish failed permanently: eventId={}, attempts={}, reason={}",
                    record.eventId, attempts, reason);
            return;
        }
        long backoffMs = Math.min(1_000L << Math.min(attempts, 6), 60_000L);
        LocalDateTime nextRetryAt = SqlTime.toLocal(now.plusMillis(backoffMs));
        outboxMapper.markRetry(record.id, nextRetryAt, SqlTime.toLocal(now), truncate(reason));
        log.warn("outbox publish failed, retry scheduled: eventId={}, attempts={}, backoffMs={}, reason={}",
                record.eventId, attempts, backoffMs, reason);
    }

    private void recoverStale() {
        Instant now = Instant.now();
        int recovered = outboxMapper.recoverStale(SqlTime.toLocal(now),
                SqlTime.toLocal(now.minusMillis(properties.getStaleTimeoutMs())));
        if (recovered > 0) {
            log.warn("recovered {} stale outbox publishing records", recovered);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
