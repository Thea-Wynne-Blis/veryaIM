package com.veryal.bootstrap;

import com.veryal.infrastructure.mysql.mapper.OutboxMapper;
import com.veryal.infrastructure.mysql.po.OutboxPO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox 重试选取：{@code status=2}（等待重试）的记录必须能被重新领取。
 *
 * <p>回归背景：{@code selectPendingIds} / {@code claim} 以前只认 {@code status=0}，
 * 而 {@code markRetry} / {@code recoverStale} 写入的是 2，发布失败一次事件就永久丢失。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxRetryIntegrationTest {

    @Autowired
    private OutboxMapper outboxMapper;

    @Test
    void retryableRecordsAreSelectedAndClaimed() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        long dueRetry = insert(2, now.minusSeconds(1));
        long futureRetry = insert(2, now.plusHours(1));
        long firstPublish = insert(0, now.minusSeconds(1));
        try {
            List<Long> pending = outboxMapper.selectPendingIds(now, 100_000);
            assertThat(pending).contains(dueRetry, firstPublish);
            assertThat(pending).doesNotContain(futureRetry);

            // 退避到期的重试记录可以被抢占；抢占后同一轮不能被重复领取。
            assertThat(outboxMapper.claim(dueRetry, now)).isEqualTo(1);
            assertThat(outboxMapper.selectById(dueRetry).status).isEqualTo(4);
            assertThat(outboxMapper.claim(dueRetry, now)).isZero();
        } finally {
            for (long id : new long[]{dueRetry, futureRetry, firstPublish}) {
                outboxMapper.deleteById(id);
            }
        }
    }

    private long insert(int status, LocalDateTime nextRetryAt) {
        OutboxPO po = new OutboxPO();
        po.id = System.nanoTime();
        po.eventId = "outbox-retry-test-" + po.id;
        po.aggregateId = "outbox-retry-test";
        po.eventType = "test.event";
        po.payload = new byte[]{1};
        po.status = status;
        po.retryCount = 0;
        po.nextRetryAt = nextRetryAt;
        po.createdAt = LocalDateTime.now().withNano(0);
        po.updatedAt = po.createdAt;
        outboxMapper.insert(po);
        return po.id;
    }
}
