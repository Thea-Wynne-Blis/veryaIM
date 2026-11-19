package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.moment.MomentSetting;
import com.veryal.domain.moment.MomentSettingRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.MomentSettingMapper;
import com.veryal.infrastructure.mysql.po.MomentSettingPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 朋友圈个人设置的 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisMomentSettingRepository implements MomentSettingRepository {

    private final MomentSettingMapper momentSettingMapper;

    @Override
    public Optional<MomentSetting> find(long userId) {
        return Optional.ofNullable(momentSettingMapper.selectById(userId))
                .map(this::toDomain);
    }

    @Override
    public void save(MomentSetting setting) {
        MomentSettingPO po = toPO(setting);
        MomentSettingPO existing = momentSettingMapper.selectById(setting.userId());
        if (existing == null) {
            try {
                momentSettingMapper.insert(po);
                return;
            } catch (DuplicateKeyException e) {
                // 并发首次写入：退回更新
            }
        }
        momentSettingMapper.update(null, Wrappers.<MomentSettingPO>lambdaUpdate()
                .eq(MomentSettingPO::getUserId, setting.userId())
                .set(MomentSettingPO::getVisibleDays, setting.visibleDays())
                .set(MomentSettingPO::getLastReadMomentId, setting.lastReadMomentId())
                .set(MomentSettingPO::getUpdatedAt, SqlTime.toLocal(setting.updatedAt())));
    }

    private MomentSetting toDomain(MomentSettingPO po) {
        return new MomentSetting(po.userId, po.visibleDays, po.lastReadMomentId,
                SqlTime.toInstant(po.updatedAt));
    }

    private MomentSettingPO toPO(MomentSetting setting) {
        MomentSettingPO po = new MomentSettingPO();
        po.userId = setting.userId();
        po.visibleDays = setting.visibleDays();
        po.lastReadMomentId = setting.lastReadMomentId();
        po.updatedAt = SqlTime.toLocal(setting.updatedAt());
        return po;
    }
}
