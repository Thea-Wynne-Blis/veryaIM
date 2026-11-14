package com.veryal.application.identity;

/**
 * 登录命令。deviceId 由客户端生成并持久化，用于多端识别。
 */
public record LoginCommand(String username, String password, String deviceId,
                           String platform, String deviceName) {
}
