package com.veryal.access.http.dto;

import com.veryal.application.contact.ContactApplicationService;

import java.util.List;

/**
 * 通讯录响应。
 */
public record ContactResponse(List<FriendResponse> friends, List<GroupContactResponse> groups,
                              long pendingRequests) {

    public static ContactResponse from(ContactApplicationService.ContactView view) {
        return new ContactResponse(
                view.friends().stream().map(FriendResponse::from).toList(),
                view.groups().stream().map(GroupContactResponse::from).toList(),
                view.pendingRequests());
    }

    public record GroupContactResponse(String groupId, String name) {

        public static GroupContactResponse from(ContactApplicationService.GroupContact view) {
            return new GroupContactResponse(view.groupId(), view.name());
        }
    }
}
