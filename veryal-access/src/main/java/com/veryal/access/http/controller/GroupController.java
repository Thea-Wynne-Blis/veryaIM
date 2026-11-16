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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 群组接口。群会话复用消息接口发送消息。
 */
@Tag(name = "群聊")
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupApplicationService groupApplicationService;

    @Operation(summary = "创建群聊", description = "创建后自动把创建者设为群主，群 ID 即会话 ID，可直接用于发消息。")
    @PostMapping
    public ApiResponse<GroupResponse> create(@CurrentUser AuthenticatedUser user,
                                             @Valid @RequestBody CreateGroupRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.create(
                user.userId(), request.name(), request.memberIds())));
    }

    @Operation(summary = "我的群列表", description = "返回我加入的全部群。")
    @GetMapping
    public ApiResponse<List<GroupSummaryResponse>> list(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(groupApplicationService.list(user.userId()).stream()
                .map(GroupSummaryResponse::from)
                .toList());
    }

    @Operation(summary = "群详情", description = "返回群名、群主、成员数与我的角色。")
    @GetMapping("/{groupId}")
    public ApiResponse<GroupResponse> get(@CurrentUser AuthenticatedUser user,
                                          @PathVariable String groupId) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.get(
                user.userId(), groupId)));
    }

    @Operation(summary = "邀请成员", description = "群主或管理员可拉人入群。群成员上限 1000。")
    @PostMapping("/{groupId}/members")
    public ApiResponse<GroupResponse> addMembers(@CurrentUser AuthenticatedUser user,
                                                 @PathVariable String groupId,
                                                 @Valid @RequestBody AddGroupMembersRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.addMembers(
                user.userId(), groupId, request.userIds())));
    }

    @Operation(summary = "修改群信息", description = "仅群主或管理员可改群名。")
    @PutMapping("/{groupId}")
    public ApiResponse<GroupResponse> rename(@CurrentUser AuthenticatedUser user,
                                             @PathVariable String groupId,
                                             @Valid @RequestBody UpdateGroupRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.rename(
                user.userId(), groupId, request.name())));
    }

    @Operation(summary = "退出群聊", description = "群主需先转让群主身份才能退群。")
    @PostMapping("/{groupId}/leave")
    public ApiResponse<Void> leave(@CurrentUser AuthenticatedUser user, @PathVariable String groupId) {
        groupApplicationService.leave(user.userId(), groupId);
        return ApiResponse.ok();
    }

    @Operation(summary = "移除成员", description = "群主或管理员可移除成员。")
    @DeleteMapping("/{groupId}/members/{userId}")
    public ApiResponse<GroupResponse> kick(@CurrentUser AuthenticatedUser user,
                                           @PathVariable String groupId,
                                           @PathVariable long userId) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.kick(
                user.userId(), groupId, userId)));
    }

    @Operation(summary = "设置成员角色", description = "群主可授予或撤销管理员。")
    @PutMapping("/{groupId}/members/{userId}/role")
    public ApiResponse<GroupResponse> changeRole(@CurrentUser AuthenticatedUser user,
                                                 @PathVariable String groupId,
                                                 @PathVariable long userId,
                                                 @Valid @RequestBody GroupRoleRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.changeRole(
                user.userId(), groupId, userId, parseRole(request.role()))));
    }

    @Operation(summary = "转让群主", description = "把群主身份转给指定成员。")
    @PostMapping("/{groupId}/transfer")
    public ApiResponse<GroupResponse> transfer(@CurrentUser AuthenticatedUser user,
                                               @PathVariable String groupId,
                                               @Valid @RequestBody TransferGroupRequest request) {
        return ApiResponse.ok(GroupResponse.from(groupApplicationService.transferOwner(
                user.userId(), groupId, request.newOwnerId())));
    }

    @Operation(summary = "解散群聊", description = "仅群主可解散，解散后会话不再可用。")
    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> disband(@CurrentUser AuthenticatedUser user,
                                     @PathVariable String groupId) {
        groupApplicationService.disband(user.userId(), groupId);
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
