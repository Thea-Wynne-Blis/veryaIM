package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.moderation.SensitiveWord;
import com.veryal.domain.moderation.SensitiveWordRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.SensitiveWordMapper;
import com.veryal.infrastructure.mysql.po.SensitiveWordPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 敏感词 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisSensitiveWordRepository implements SensitiveWordRepository {

    private final SensitiveWordMapper wordMapper;

    @Override
    public List<SensitiveWord> list(int limit) {
        return wordMapper.selectList(Wrappers.<SensitiveWordPO>lambdaQuery()
                        .orderByDesc(SensitiveWordPO::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 1000))))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<SensitiveWord> listEnabled() {
        return wordMapper.selectList(Wrappers.<SensitiveWordPO>lambdaQuery()
                        .eq(SensitiveWordPO::getEnabled, 1))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void save(SensitiveWord word) {
        SensitiveWordPO po = new SensitiveWordPO();
        po.id = word.id();
        po.word = word.word();
        po.enabled = word.enabled() ? 1 : 0;
        po.createdAt = SqlTime.toLocal(word.createdAt());
        wordMapper.insert(po);
    }

    @Override
    public void delete(long id) {
        wordMapper.deleteById(id);
    }

    private SensitiveWord toDomain(SensitiveWordPO po) {
        return new SensitiveWord(po.id, po.word, po.enabled == 1,
                SqlTime.toInstant(po.createdAt));
    }
}
