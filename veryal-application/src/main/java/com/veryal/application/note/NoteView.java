package com.veryal.application.note;

import java.time.Instant;

/**
 * 笔记完整内容。正文为 Markdown 原文，来自文件存储。
 */
public record NoteView(long id, String title, String content,
                       Instant createdAt, Instant updatedAt) {

    public static NoteView from(com.veryal.domain.note.Note note, String content) {
        return new NoteView(note.id(), note.title(), content,
                note.createdAt(), note.updatedAt());
    }
}
