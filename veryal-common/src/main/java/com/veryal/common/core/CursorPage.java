package com.veryal.common.core;

import java.util.List;
import java.util.function.ToLongFunction;

/**
 * 游标分页结果。VERYAL 禁止使用 OFFSET 分页读取消息历史。
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }

    /**
     * 用「多取一条」的结果切出当前页：超出部分丢弃，并给出下一页游标。
     *
     * <p>消息历史、会话内搜索、离线同步都按 {@code size + 1} 查询来判断是否还有下一页，
     * 截断与游标计算放在这里，避免每个调用点重复同一段边界处理。</p>
     *
     * @param fetched     仓库按 {@code pageSize + 1} 取回的结果
     * @param pageSize    本页上限
     * @param emptyCursor 结果为空时沿用的游标（通常是本次请求的起始水位）
     * @param cursorOf    取单条记录游标值的方法
     */
    public static <T> CursorPage<T> slice(List<T> fetched, int pageSize, long emptyCursor,
                                          ToLongFunction<T> cursorOf) {
        boolean hasMore = fetched.size() > pageSize;
        List<T> items = hasMore ? fetched.subList(0, pageSize) : fetched;
        long next = items.isEmpty()
                ? emptyCursor : cursorOf.applyAsLong(items.get(items.size() - 1));
        return new CursorPage<>(items, Long.toString(next), hasMore);
    }
}
