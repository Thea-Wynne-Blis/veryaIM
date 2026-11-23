package com.veryal.application.note;

import java.time.Instant;

/**
 * 笔记列表项：只带标题与摘要，点开后再请求完整正文。
 */
public record NoteSummaryView(long id, String title, String excerpt,
                              Instant createdAt, Instant updatedAt) {
}
