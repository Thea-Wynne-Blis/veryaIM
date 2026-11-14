package com.veryal.application.identity;

/**
 * 注册命令。
 */
public record RegisterCommand(String username, String password, String nickname) {
}
