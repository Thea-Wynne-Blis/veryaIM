package com.veryal.application.group;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.group.GroupMember;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.group.GroupRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 群身份校验：控制器注解与服务层共用同一套角色矩阵。
 *
 * <p>角色大小按 {@link GroupRole#code()} 比较：MEMBER 0 &lt; ADMIN 1 &lt; OWNER 2，
 * {@code requireRole(groupId, userId, ADMIN)} 表示「至少管理员」。</p>
 */
@Service
@RequiredArgsConstructor
public class GroupAccessGuard {

    private final GroupRepository groupRepository;

    /**
     * 校验并返回群成员身份；不是群成员抛 40301。
     *
     * <p>群相关的每个服务都需要这段「不是成员就 40301」的判断，
     * 统一放在 Guard 里，避免各处复制同一段异常构造。</p>
     */
    public GroupMember requireMember(String groupId, long userId) {
        return groupRepository.findMember(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER,
                        "not a group member"));
    }

    /**
     * 校验当前用户在群里的身份，不足则抛 403；不是群成员抛 40301。
     */
    public GroupRole requireRole(String groupId, long userId, GroupRole required) {
        GroupMember member = requireMember(groupId, userId);
        if (member.role().code() < required.code()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    required == GroupRole.OWNER
                            ? "only group owner can perform this action"
                            : "only group owner or admin can perform this action");
        }
        return member.role();
    }
}
