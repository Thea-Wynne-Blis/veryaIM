package com.veryal.common.observability;

import com.veryal.common.core.RandomIds;
import org.slf4j.MDC;

/**
 * 基于 SLF4J MDC 的链路上下文。
 *
 * <p>统一日志字段见 {@link LogFields}。线程池任务必须在提交时捕获并在执行时恢复上下文，
 * 接入层通过 {@link #wrap(Runnable)} 包装业务任务。</p>
 */
public final class TraceContext {

    private TraceContext() {
    }

    /**
     * 开始一条新链路：生成 traceId 写入 MDC，并返回可关闭的作用域。
     */
    public static Scope start(String traceId) {
        String previous = MDC.get(LogFields.TRACE_ID);
        String actual = (traceId == null || traceId.isBlank()) ? newTraceId() : traceId;
        MDC.put(LogFields.TRACE_ID, actual);
        return new Scope(LogFields.TRACE_ID, previous);
    }

    public static String currentTraceId() {
        return MDC.get(LogFields.TRACE_ID);
    }

    public static void put(String key, String value) {
        if (value == null || value.isEmpty()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    public static void clear() {
        MDC.clear();
    }

    /**
     * 将当前 MDC 上下文复制到任务执行线程。
     */
    public static Runnable wrap(Runnable task) {
        var captured = MDC.getCopyOfContextMap();
        return () -> {
            var previous = MDC.getCopyOfContextMap();
            if (captured != null) {
                MDC.setContextMap(captured);
            }
            try {
                task.run();
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }

    private static String newTraceId() {
        return RandomIds.newId();
    }

    /**
     * MDC 作用域，关闭时恢复进入前的值。
     */
    public static final class Scope implements AutoCloseable {

        private final String key;
        private final String previous;

        private Scope(String key, String previous) {
            this.key = key;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        }
    }
}
