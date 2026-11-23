package com.veryal.domain.note;

import java.time.Instant;

/**
 * 一条个人笔记。正文是 Markdown 原文，保存在文件存储里，这里只留对象键与摘要。
 *
 * @param objectKey 正文在文件存储中的对象键
 * @param sizeBytes 正文字节数
 * @param excerpt   正文纯文本摘要，列表与搜索用
 */
public record Note(long id, long userId, String title, String objectKey, long sizeBytes,
                   String excerpt, Instant createdAt, Instant updatedAt) {
}
