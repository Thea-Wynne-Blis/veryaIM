package com.veryal.access.http.controller;

import com.veryal.access.http.dto.AddGroupMembersRequest;
import com.veryal.access.http.dto.CreateGroupRequest;
import com.veryal.access.http.dto.GroupResponse;
import com.veryal.access.http.dto.GroupRoleRequest;
import com.veryal.access.http.dto.GroupSummaryResponse;
import com.veryal.access.http.dto.TransferGroupRequest;
import com.veryal.access.http.dto.UpdateGroupRequest;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.group.GroupApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.group.GroupRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

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

    @PutMapping("/{groupId}")
    public ApiResponse<GroupResponse> rename(@CurrentUser AuthenticatedUser user,
                                             @PathVariable String groupId,
                                             @Valid @RequestBody UpdateGroupRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.rename(
                user.tenantId(), user.userId(), groupId, request.name())));
    }

    @PostMapping("/{groupId}/leave")
    public ApiResponse<Void> leave(@CurrentUser AuthenticatedUser user, @PathVariable String groupId) {
        groupApplicationService.leave(user.tenantId(), user.userId(), groupId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ApiResponse<GroupResponse> kick(@CurrentUser AuthenticatedUser user,
                                           @PathVariable String groupId,
                                           @PathVariable long userId) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.kick(
                user.tenantId(), user.userId(), groupId, userId)));
    }

    @PutMapping("/{groupId}/members/{userId}/role")
    public ApiResponse<GroupResponse> changeRole(@CurrentUser AuthenticatedUser user,
                                                 @PathVariable String groupId,
                                                 @PathVariable long userId,
                                                 @Valid @RequestBody GroupRoleRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.changeRole(
                user.tenantId(), user.userId(), groupId, userId, parseRole(request.role()))));
    }

    @PostMapping("/{groupId}/transfer")
    public ApiResponse<GroupResponse> transfer(@CurrentUser AuthenticatedUser user,
                                               @PathVariable String groupId,
                                               @Valid @RequestBody TransferGroupRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.transferOwner(
                user.tenantId(), user.userId(), groupId, request.newOwnerId())));
    }

    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> disband(@CurrentUser AuthenticatedUser user,
                                     @PathVariable String groupId) {
        groupApplicationService.disband(user.tenantId(), user.userId(), groupId);
        return ApiResponse.ok();
    }

    private GroupRole parseRole(String role) {
        try {
            GroupRole parsed = GroupRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
            if (parsed == GroupRole.OWNER) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot assign owner role");
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "unsupported role: " + role);
        }
    }
}
