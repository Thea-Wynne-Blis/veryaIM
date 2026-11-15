package com.veryal.access.http.dto;

import com.veryal.application.tenant.TenantDataService;
import com.veryal.application.tenant.TenantAdminService;

import java.time.Instant;
import java.util.List;

/**
 * 租户数据导出（元数据 + 用户/群/管理员，不含消息正文）。
 */
public record TenantExportResponse(long tenantId, String tenantCode, String tenantName,
                                   String tenantStatus, long users, long groups, long messages,
                                   long storageBytes, long admins, long estimatedMonthlyCents,
                                   List<ExportUser> userList, List<ExportGroup> groupList,
                                   List<Long> adminUserIds, Instant exportedAt) {

    public static TenantExportResponse from(TenantDataService.TenantExportView view) {
        TenantAdminService.TenantUsageView usage = view.usage();
        return new TenantExportResponse(view.tenantId(), view.tenantCode(), view.tenantName(),
                view.tenantStatus(), usage.users(), usage.groups(), usage.messages(),
                usage.storageBytes(), usage.admins(), usage.estimatedMonthlyCents(),
                view.users().stream().map(ExportUser::from).toList(),
                view.groups().stream().map(ExportGroup::from).toList(),
                view.adminUserIds(), view.exportedAt());
    }

    public record ExportUser(long userId, String username, String nickname, String avatar,
                             String status, Instant createdAt) {

        static ExportUser from(TenantDataService.ExportUser user) {
            return new ExportUser(user.userId(), user.username(), user.nickname(), user.avatar(),
                    user.status(), user.createdAt());
        }
    }

    public record ExportGroup(String groupId, String name, long ownerId, Instant createdAt) {

        static ExportGroup from(TenantDataService.ExportGroup group) {
            return new ExportGroup(group.groupId(), group.name(), group.ownerId(),
                    group.createdAt());
        }
    }
}
