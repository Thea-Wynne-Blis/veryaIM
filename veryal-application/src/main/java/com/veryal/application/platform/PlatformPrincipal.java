package com.veryal.application.platform;

import com.veryal.domain.platform.PlatformAdminRole;

/**
 * 平台管理员身份。
 */
public record PlatformPrincipal(long adminId, String username, PlatformAdminRole role) {
}
