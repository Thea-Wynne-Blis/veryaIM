package com.veryal.application.moderation;

import com.veryal.application.platform.PlatformAuditRecorder;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.json.JsonUtils;
import com.veryal.common.moderation.DfaSensitiveWordFilter;
import com.veryal.domain.message.MessageType;
import com.veryal.domain.moderation.SensitiveWord;
import com.veryal.domain.moderation.SensitiveWordRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 敏感词内容审核，DFA 构建内存 Trie，词库变更后重建。
 */
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private static final int MAX_WORD_LENGTH = 64;

    private final SensitiveWordRepository wordRepository;
    private final PlatformAuditRecorder auditRecorder;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;
    private final AtomicReference<DfaSensitiveWordFilter> filter =
            new AtomicReference<>(new DfaSensitiveWordFilter(List.of()));

    @PostConstruct
    void load() {
        refresh();
    }

    public synchronized void refresh() {
        filter.set(new DfaSensitiveWordFilter(wordRepository.listEnabled().stream()
                .map(SensitiveWord::word)
                .toList()));
    }

    public List<SensitiveWord> list(int limit) {
        return wordRepository.list(limit);
    }

    @Transactional
    public SensitiveWord add(long adminId, String word) {
        String value = word == null ? "" : word.trim();
        if (value.isEmpty() || value.length() > MAX_WORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "sensitive word must be 1-" + MAX_WORD_LENGTH + " characters");
        }
        SensitiveWord created = new SensitiveWord(idGenerator.nextId(), value, true,
                timeProvider.now());
        try {
            wordRepository.save(created);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "sensitive word already exists");
        }
        refresh();
        auditRecorder.record(adminId, "OPS_SENSITIVE_WORD_ADD", "sensitive_word",
                Long.toString(created.id()), value);
        return created;
    }

    @Transactional
    public void delete(long adminId, long id) {
        wordRepository.delete(id);
        refresh();
        auditRecorder.record(adminId, "OPS_SENSITIVE_WORD_DELETE", "sensitive_word",
                Long.toString(id), null);
    }

    public void checkMessage(MessageType type, byte[] content) {
        if (type != MessageType.TEXT || content == null || content.length == 0) {
            return;
        }
        String text = extractText(content);
        if (text != null && filter.get().contains(text)) {
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT);
        }
    }

    /** 校验任意用户输入文本（朋友圈正文、评论等）。 */
    public void checkText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (filter.get().contains(text)) {
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT);
        }
    }

    private String extractText(byte[] content) {
        String raw = new String(content, StandardCharsets.UTF_8).trim();
        if (!raw.startsWith("{")) {
            return raw;
        }
        try {
            var node = JsonUtils.mapper().readTree(raw).get("text");
            return node == null ? raw : node.asText();
        } catch (Exception e) {
            return raw;
        }
    }
}
