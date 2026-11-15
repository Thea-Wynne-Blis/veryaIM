package com.veryal.domain.identity;

import java.util.Optional;

/**
 * 用户隐私设置仓储。
 */
public interface UserPrivacyRepository {

    Optional<UserPrivacy> findByUserId(long userId);

    void save(UserPrivacy privacy);
}
