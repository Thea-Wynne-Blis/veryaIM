package com.veryal.application.contact;

import com.veryal.application.friend.FriendApplicationService;
import com.veryal.application.friend.FriendView;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通讯录聚合：好友、群、待处理好友申请。
 */
@Service
@RequiredArgsConstructor
public class ContactApplicationService {

    private final FriendApplicationService friendApplicationService;
    private final GroupRepository groupRepository;

    public ContactView contacts(long userId) {
        List<FriendView> friends = friendApplicationService.listFriends(userId);
        List<GroupContact> groups = groupRepository.findByUser(userId).stream()
                .map(group -> new GroupContact(group.id(), group.name()))
                .toList();
        long pending = friendApplicationService.pendingIncoming(userId);
        return new ContactView(friends, groups, pending);
    }

    public record ContactView(List<FriendView> friends, List<GroupContact> groups,
                              long pendingRequests) {
    }

    public record GroupContact(String groupId, String name) {
    }
}
