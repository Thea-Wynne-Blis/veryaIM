package com.veryal.common.id;

import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.SystemException;

/**
 * 雪花 ID 生成器。
 *
 * <p>结构：41 bit 毫秒时间戳 + 10 bit 节点 + 12 bit 序列号，单节点每毫秒最多 4096 个 ID。
 * 时间回拨容忍 5ms，超过则拒绝生成，避免产生重复 ID。</p>
 */
public final class SnowflakeIdGenerator implements IdGenerator {

    private static final long EPOCH = 1_704_067_200_000L; // 2024-01-01T00:00:00Z
    private static final long NODE_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_NODE_ID = (1L << NODE_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long NODE_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_BITS;
    private static final long MAX_CLOCK_BACKWARD_MS = 5L;

    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                    "nodeId must be in [0, " + MAX_NODE_ID + "], actual=" + nodeId);
        }
        this.nodeId = nodeId;
    }

    @Override
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > MAX_CLOCK_BACKWARD_MS) {
                throw new SystemException(ErrorCode.INTERNAL_ERROR,
                        "clock moved backwards " + offset + "ms, refuse to generate id");
            }
            timestamp = waitUntil(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_SHIFT)
                | sequence;
    }

    private long waitUntil(long targetTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp < targetTimestamp) {
            Thread.onSpinWait();
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
