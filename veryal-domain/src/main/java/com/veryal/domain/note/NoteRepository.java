package com.veryal.domain.note;

import java.util.List;
import java.util.Optional;

/**
 * 笔记仓储。笔记只对本人可见，按最近更新时间倒序。
 *
 * <p>正文放在文件存储里，仓储只负责元数据行；摘要用于列表展示与搜索。</p>
 */
public interface NoteRepository {

    void save(Note note);

    void update(Note note);

    Optional<Note> findById(long noteId);

    List<Note> listByUser(long userId, String keyword, int limit);

    void delete(long userId, long noteId);
}
