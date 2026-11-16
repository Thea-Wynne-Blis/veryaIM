package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.push.WebPushSubscription;
import com.veryal.domain.push.WebPushSubscriptionRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.WebPushSubscriptionMapper;
import com.veryal.infrastructure.mysql.po.WebPushSubscriptionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Web Push 订阅 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisWebPushSubscriptionRepository implements WebPushSubscriptionRepository {

    private final WebPushSubscriptionMapper mapper;

    @Override
    public void upsert(WebPushSubscription subscription) {
        WebPushSubscriptionPO existing = mapper.selectOne(
                Wrappers.<WebPushSubscriptionPO>lambdaQuery()
                        .eq(WebPushSubscriptionPO::getEndpointHash, subscription.endpointHash()));
        WebPushSubscriptionPO po = toPO(subscription);
        if (existing == null) {
            mapper.insert(po);
        } else {
            po.id = existing.id;
            po.createdAt = existing.createdAt;
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<WebPushSubscription> findByEndpointHash(String endpointHash) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<WebPushSubscriptionPO>lambdaQuery()
                        .eq(WebPushSubscriptionPO::getEndpointHash, endpointHash)))
                .map(this::toDomain);
    }

    @Override
    public List<WebPushSubscription> listEnabled(long userId) {
        return mapper.selectList(Wrappers.<WebPushSubscriptionPO>lambdaQuery()
                        .eq(WebPushSubscriptionPO::getUserId, userId)
                        .eq(WebPushSubscriptionPO::getEnabled, 1)
                        .orderByDesc(WebPushSubscriptionPO::getUpdatedAt))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void delete(String endpointHash) {
        mapper.delete(Wrappers.<WebPushSubscriptionPO>lambdaQuery()
                .eq(WebPushSubscriptionPO::getEndpointHash, endpointHash));
    }

    @Override
    public void deleteById(long id) {
        mapper.deleteById(id);
    }

    private WebPushSubscription toDomain(WebPushSubscriptionPO po) {
        return new WebPushSubscription(po.id, po.userId, po.endpoint, po.endpointHash, po.p256dh,
                po.auth, po.userAgent, po.enabled == 1, SqlTime.toInstant(po.createdAt),
                SqlTime.toInstant(po.updatedAt));
    }

    private WebPushSubscriptionPO toPO(WebPushSubscription s) {
        WebPushSubscriptionPO po = new WebPushSubscriptionPO();
        po.id = s.id();
        po.userId = s.userId();
        po.endpoint = s.endpoint();
        po.endpointHash = s.endpointHash();
        po.p256dh = s.p256dh();
        po.auth = s.auth();
        po.userAgent = s.userAgent();
        po.enabled = s.enabled() ? 1 : 0;
        po.createdAt = SqlTime.toLocal(s.createdAt());
        po.updatedAt = SqlTime.toLocal(s.updatedAt());
        return po;
    }
}
