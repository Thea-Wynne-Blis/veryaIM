package com.veryal.common.core;

import java.time.Instant;

/**
 * 可注入的时间源，便于测试与统一使用 UTC。
 */
public interface TimeProvider {

    Instant now();

    static TimeProvider system() {
        return Instant::now;
    }

    static TimeProvider fixed(Instant instant) {
        return () -> instant;
    }

    default long nowMillis() {
        return now().toEpochMilli();
    }
}
