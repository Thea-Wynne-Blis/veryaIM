package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.note.Note;
import com.veryal.domain.note.NoteRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.NoteMapper;
import com.veryal.infrastructure.mysql.po.NotePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 笔记仓储 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisNoteRepository implements NoteRepository {

    private static final int MAX_LIST_SIZE = 300;

    private final NoteMapper noteMapper;

    @Override
    public void save(Note note) {
        noteMapper.insert(toPO(note));
    }

    @Override
    public void update(Note note) {
        noteMapper.updateById(toPO(note));
    }

    @Override
    public Optional<Note> findById(long noteId) {
        return Optional.ofNullable(noteMapper.selectById(noteId)).map(this::toDomain);
    }

    @Override
    public List<Note> listByUser(long userId, String keyword, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIST_SIZE));
        var query = Wrappers.<NotePO>lambdaQuery()
                .eq(NotePO::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            // 正文在对象存储里，SQL 只能搜标题与摘要；正文关键词在客户端本地过滤。
            String like = keyword.trim();
            query.and(wrapper -> wrapper.like(NotePO::getTitle, like)
                    .or()
                    .like(NotePO::getExcerpt, like));
        }
        return noteMapper.selectList(query
                        .orderByDesc(NotePO::getUpdatedAt)
                        .last("LIMIT " + size))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void delete(long userId, long noteId) {
        noteMapper.delete(Wrappers.<NotePO>lambdaQuery()
                .eq(NotePO::getUserId, userId)
                .eq(NotePO::getId, noteId));
    }

    private Note toDomain(NotePO po) {
        return new Note(po.id, po.userId, po.title, po.objectKey,
                po.sizeBytes == null ? 0L : po.sizeBytes, po.excerpt == null ? "" : po.excerpt,
                SqlTime.toInstant(po.createdAt), SqlTime.toInstant(po.updatedAt));
    }

    private NotePO toPO(Note note) {
        NotePO po = new NotePO();
        po.id = note.id();
        po.userId = note.userId();
        po.title = note.title();
        po.objectKey = note.objectKey();
        po.sizeBytes = note.sizeBytes();
        po.excerpt = note.excerpt();
        po.createdAt = SqlTime.toLocal(note.createdAt());
        po.updatedAt = SqlTime.toLocal(note.updatedAt());
        return po;
    }
}
