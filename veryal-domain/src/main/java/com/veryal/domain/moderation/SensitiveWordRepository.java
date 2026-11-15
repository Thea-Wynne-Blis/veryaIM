package com.veryal.domain.moderation;

import java.util.List;

/**
 * 敏感词仓储。
 */
public interface SensitiveWordRepository {

    List<SensitiveWord> list(int limit);

    List<SensitiveWord> listEnabled();

    void save(SensitiveWord word);

    void delete(long id);
}
