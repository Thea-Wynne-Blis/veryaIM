package com.veryal.access.http.dto;

/**
 * 更新隐私设置；字段为空表示保持原值。
 */
public record UpdatePrivacyRequest(Boolean searchByPhone, Boolean searchByUsername,
                                   String friendPolicy, Boolean showOnline, Boolean readReceipt,
                                   Boolean invitedToGroup) {
}
