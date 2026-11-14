package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.conversation.Conversation;
import com.veryal.domain.conversation.ConversationMember;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.conversation.ConversationType;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.ConversationMapper;
import com.veryal.infrastructure.mysql.mapper.ConversationMemberMapper;
import com.veryal.infrastructure.mysql.po.ConversationMemberPO;
import com.veryal.infrastructure.mysql.po.ConversationPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话仓储 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisConversationRepository implements ConversationRepository {

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper memberMapper;

    @Override
    public Optional<Conversation> findById(String conversationId) {
        return Optional.ofNullable(conversationMapper.selectById(conversationId)).map(this::toDomain);
    }

    @Override
    public boolean saveIfAbsent(Conversation conversation) {
        return conversationMapper.insertIgnore(toPO(conversation)) > 0;
    }

    @Override
    public void touch(String conversationId, Instant updatedAt) {
        conversationMapper.update(null, Wrappers.<ConversationPO>lambdaUpdate()
                .eq(ConversationPO::getId, conversationId)
                .set(ConversationPO::getUpdatedAt, SqlTime.toLocal(updatedAt)));
    }

    @Override
    public List<ConversationMember> findMembers(String conversationId) {
        return memberMapper.selectList(Wrappers.<ConversationMemberPO>lambdaQuery()
                        .eq(ConversationMemberPO::getConversationId, conversationId)
                        .orderByAsc(ConversationMemberPO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ConversationMember> findMember(String conversationId, long userId) {
        return Optional.ofNullable(memberMapper.selectOne(Wrappers.<ConversationMemberPO>lambdaQuery()
                        .eq(ConversationMemberPO::getConversationId, conversationId)
                        .eq(ConversationMemberPO::getUserId, userId)))
                .map(this::toDomain);
    }

    @Override
    public void addMember(ConversationMember member) {
        memberMapper.insertIgnore(toPO(member));
    }

    @Override
    public void removeMember(String conversationId, long userId) {
        memberMapper.delete(Wrappers.<ConversationMemberPO>lambdaQuery()
                .eq(ConversationMemberPO::getConversationId, conversationId)
                .eq(ConversationMemberPO::getUserId, userId));
    }

    @Override
    public void updateMemberCursor(ConversationMember member) {
        memberMapper.updateById(toPO(member));
    }

    @Override
    public List<Conversation> findByUser(long tenantId, long userId) {
        return conversationMapper.selectByUser(tenantId, userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(String conversationId) {
        memberMapper.delete(Wrappers.<ConversationMemberPO>lambdaQuery()
                .eq(ConversationMemberPO::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    private Conversation toDomain(ConversationPO po) {
        return new Conversation(po.id, po.tenantId, ConversationType.fromCode(po.conversationType),
                SqlTime.toInstant(po.createdAt), SqlTime.toInstant(po.updatedAt));
    }

    private ConversationPO toPO(Conversation conversation) {
        ConversationPO po = new ConversationPO();
        po.id = conversation.id();
        po.tenantId = conversation.tenantId();
        po.conversationType = conversation.type().code();
        po.createdAt = SqlTime.toLocal(conversation.createdAt());
        po.updatedAt = SqlTime.toLocal(conversation.updatedAt());
        return po;
    }

    private ConversationMember toDomain(ConversationMemberPO po) {
        return new ConversationMember(po.id, po.conversationId, po.userId, po.lastReadSeq, po.lastSyncSeq,
                SqlTime.toInstant(po.joinedAt));
    }

    private ConversationMemberPO toPO(ConversationMember member) {
        ConversationMemberPO po = new ConversationMemberPO();
        po.id = member.id();
        po.conversationId = member.conversationId();
        po.userId = member.userId();
        po.lastReadSeq = member.lastReadSeq();
        po.lastSyncSeq = member.lastSyncSeq();
        po.joinedAt = SqlTime.toLocal(member.joinedAt());
        return po;
    }
}
