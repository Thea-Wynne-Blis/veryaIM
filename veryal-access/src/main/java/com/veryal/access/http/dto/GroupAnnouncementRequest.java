package com.veryal.access.http.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新群公告。
 */
public record GroupAnnouncementRequest(@Size(max = 2000) String announcement) {
}
