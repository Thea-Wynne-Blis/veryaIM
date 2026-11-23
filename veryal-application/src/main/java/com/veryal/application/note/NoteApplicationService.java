package com.veryal.application.note;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.file.FileStorage;
import com.veryal.domain.file.FileStorageProvider;
import com.veryal.domain.note.Note;
import com.veryal.domain.note.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 个人笔记应用服务。
 *
 * <p>正文按 Markdown 原文写进文件存储（local / MinIO / OSS），数据库只保留标题、
 * 摘要与对象键；标题为空时自动从正文首行提取。列表只返回摘要，点开后才读取文件。</p>
 */
@Service
@RequiredArgsConstructor
public class NoteApplicationService {

    private static final int MAX_LIST_SIZE = 300;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_EXCERPT_LENGTH = 400;
    private static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;
    private static final String UNTITLED = "无标题笔记";
    private static final String CONTENT_TYPE = "text/markdown; charset=utf-8";
    private static final String OBJECT_KEY_PREFIX = "notes/";

    private static final Pattern EMPTY_LINE = Pattern.compile("(?m)^\\s*$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final NoteRepository noteRepository;
    private final FileStorageProvider fileStorageProvider;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public NoteView create(long userId, String title, String content) {
        String body = content == null ? "" : content;
        byte[] bytes = encode(body);
        Instant now = timeProvider.now();
        long noteId = idGenerator.nextId();
        String objectKey = OBJECT_KEY_PREFIX + userId + "/" + noteId + ".md";
        storage().put(objectKey, bytes, CONTENT_TYPE);
        Note note = new Note(noteId, userId, titleOf(title, body), objectKey, bytes.length,
                excerptOf(body), now, now);
        noteRepository.save(note);
        return NoteView.from(note, body);
    }

    @Transactional
    public NoteView update(long userId, long noteId, String title, String content) {
        Note existing = requireOwned(userId, noteId);
        String body;
        int sizeBytes;
        String excerpt;
        if (content == null) {
            // 只改标题：正文不动，也不再触发对象存储写入。
            body = readContent(existing);
            sizeBytes = (int) existing.sizeBytes();
            excerpt = existing.excerpt();
        } else {
            byte[] bytes = encode(content);
            body = content;
            sizeBytes = bytes.length;
            excerpt = excerptOf(content);
            storage().put(existing.objectKey(), bytes, CONTENT_TYPE);
        }
        Note updated = new Note(existing.id(), userId, titleOf(title, body), existing.objectKey(),
                sizeBytes, excerpt, existing.createdAt(), timeProvider.now());
        noteRepository.update(updated);
        return NoteView.from(updated, body);
    }

    @Transactional(readOnly = true)
    public NoteView get(long userId, long noteId) {
        Note note = requireOwned(userId, noteId);
        return NoteView.from(note, readContent(note));
    }

    @Transactional(readOnly = true)
    public List<NoteSummaryView> list(long userId, String keyword, int limit) {
        int size = limit <= 0 ? MAX_LIST_SIZE : limit;
        return noteRepository.listByUser(userId, keyword, size).stream()
                .map(note -> new NoteSummaryView(note.id(), note.title(), note.excerpt(),
                        note.createdAt(), note.updatedAt()))
                .toList();
    }

    @Transactional
    public void delete(long userId, long noteId) {
        Note note = requireOwned(userId, noteId);
        // 先删对象再删行：对象删除失败时不会留下查不到内容的笔记。
        storage().delete(note.objectKey());
        noteRepository.delete(userId, noteId);
    }

    private FileStorage storage() {
        return fileStorageProvider.current();
    }

    private Note requireOwned(long userId, long noteId) {
        return noteRepository.findById(noteId)
                .filter(note -> note.userId() == userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTE_NOT_FOUND));
    }

    /** 读取 Markdown 原文；对象丢失时按笔记不存在处理。 */
    private String readContent(Note note) {
        try {
            return new String(storage().get(note.objectKey()), StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
        }
    }

    private static byte[] encode(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTENT_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "笔记正文超过 2 MB 限制");
        }
        return bytes;
    }

    /** 显式标题优先；为空时取正文第一段有内容的文字。 */
    private static String titleOf(String title, String content) {
        String given = title == null ? "" : title.trim();
        if (!given.isEmpty()) {
            return given.length() > MAX_TITLE_LENGTH ? given.substring(0, MAX_TITLE_LENGTH) : given;
        }
        for (String line : EMPTY_LINE.split(content)) {
            String candidate = plainText(line);
            if (!candidate.isEmpty()) {
                return candidate.length() > 40 ? candidate.substring(0, 40) : candidate;
            }
        }
        return UNTITLED;
    }

    /** 列表摘要：去掉常见 Markdown 标记后压成一行。 */
    private static String excerptOf(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String text = WHITESPACE.matcher(plainText(content)).replaceAll(" ").trim();
        return text.length() > MAX_EXCERPT_LENGTH ? text.substring(0, MAX_EXCERPT_LENGTH) + "…" : text;
    }

    private static String plainText(String markdown) {
        return markdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}(#{1,6}|>|[-*+]\\s|\\d+\\.\\s)\\s*", "")
                .replaceAll("[*_~]{1,3}", "")
                .replaceAll("\\|", " ")
                .trim();
    }
}
