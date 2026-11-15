package com.veryal.access.http.dto;

/**
 * 运营禁言用户。
 */
public record OpsMuteRequest(Integer minutes, String reason) {
}
