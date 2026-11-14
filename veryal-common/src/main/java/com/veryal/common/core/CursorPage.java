package com.veryal.common.core;

import java.util.List;

/**
 * 游标分页结果。VERYAL 禁止使用 OFFSET 分页读取消息历史。
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
