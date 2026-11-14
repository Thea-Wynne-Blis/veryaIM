package com.veryal.infrastructure.mysql;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 数据库时间统一使用 UTC。领域层使用 {@link Instant}，持久化使用 {@link LocalDateTime}。
 */
public final class SqlTime {

    private SqlTime() {
    }

    public static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.toInstant(ZoneOffset.UTC);
    }
}
