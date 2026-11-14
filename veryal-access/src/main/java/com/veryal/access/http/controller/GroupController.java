package com.veryal.access.http.controller;

import com.veryal.access.http.dto.AddGroupMembersRequest;
import com.veryal.access.http.dto.CreateGroupRequest;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.GroupSummaryResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.group.GroupApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 群组接口。群会话复用消息接口发送消息。
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupApplicationService groupApplicationService;

    @PostMapping
    public ApiResponse<GroupResponse> create(@CurrentUser AuthenticatedUser user,
                                             @Valid @RequestBody CreateGroupRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.create(
                user.tenantId(), user.userId(), request.name(), request.memberIds())));
    }

    @GetMapping
    public ApiResponse<List<GroupSummaryResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(groupApplicationService.list(user.tenantId(), user.userId()).stream()
                .map(GroupSummaryResponse::from)
                .toList());
    }

    @GetMapping("/{groupId}")
    public ApiResponse<GroupResponse> get(@CurrentUser AuthenticatedUser user,
                                          @PathVariable String groupId) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.get(
                user.tenantId(), user.userId(), groupId)));
    }

    @PostMapping("/{groupId}/members")
    public ApiResponse<GroupResponse> addMembers(@CurrentUser AuthenticatedUser user,
                                                 @PathVariable String groupId,
                                                 @Valid @RequestBody AddGroupMembersRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.addMembers(
                user.tenantId(), user.userId(), groupId, request.userIds())));
    }
}
