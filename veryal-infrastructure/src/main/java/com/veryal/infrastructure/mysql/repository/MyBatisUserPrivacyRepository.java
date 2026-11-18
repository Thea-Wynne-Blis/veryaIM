package com.veryal.infrastructure.mysql.repository;

import com.veryal.domain.identity.UserPrivacy;
import com.veryal.domain.identity.UserPrivacyRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.UserPrivacyMapper;
import com.veryal.infrastructure.mysql.po.UserPrivacyPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户隐私 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisUserPrivacyRepository implements UserPrivacyRepository {

    private final UserPrivacyMapper privacyMapper;

    @Override
    public Optional<UserPrivacy> findByUserId(long userId) {
        return Optional.ofNullable(privacyMapper.selectById(userId)).map(this::toDomain);
    }

    @Override
    public List<UserPrivacy> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return privacyMapper.selectBatchIds(userIds).stream().map(this::toDomain).toList();
    }

    @Override
    public void save(UserPrivacy privacy) {
        UserPrivacyPO po = new UserPrivacyPO();
        po.userId = privacy.userId();
        po.searchByPhone = privacy.searchByPhone() ? 1 : 0;
        po.searchByUsername = privacy.searchByUsername() ? 1 : 0;
        po.friendPolicy = privacy.friendPolicy().code();
        po.showOnline = privacy.showOnline() ? 1 : 0;
        po.readReceipt = privacy.readReceipt() ? 1 : 0;
        po.invitedToGroup = privacy.invitedToGroup() ? 1 : 0;
        po.updatedAt = SqlTime.toLocal(privacy.updatedAt());
        if (privacyMapper.selectById(privacy.userId()) == null) {
            privacyMapper.insert(po);
        } else {
            privacyMapper.updateById(po);
        }
    }

    private UserPrivacy toDomain(UserPrivacyPO po) {
        return new UserPrivacy(po.userId, po.searchByPhone == 1, po.searchByUsername == 1,
                UserPrivacy.FriendPolicy.fromCode(po.friendPolicy), po.showOnline == 1,
                po.readReceipt == 1, po.invitedToGroup == 1, SqlTime.toInstant(po.updatedAt));
    }
}
