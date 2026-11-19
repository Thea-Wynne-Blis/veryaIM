package com.veryal.domain.moment;

import java.util.Optional;

/**
 * 朋友圈个人设置仓储。
 */
public interface MomentSettingRepository {

    Optional<MomentSetting> find(long userId);

    void save(MomentSetting setting);
}
