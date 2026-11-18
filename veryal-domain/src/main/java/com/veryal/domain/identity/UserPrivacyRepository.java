package com.veryal.domain.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户隐私设置仓储。
 */
public interface UserPrivacyRepository {

    Optional<UserPrivacy> findByUserId(long userId);

    /**
     * 批量查询隐私设置；未落库的用户不会出现在结果里，调用方按默认值处理。
     */
    List<UserPrivacy> findByUserIds(Collection<Long> userIds);

    void save(UserPrivacy privacy);
}
