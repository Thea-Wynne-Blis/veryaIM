package com.veryal.application.moment;

/**
 * 动态媒体视图。客户端据此拼接授权媒体地址 {@code /api/v1/moments/{momentId}/media/{fileId}}。
 */
public record MomentMediaView(long fileId, String kind, int sortOrder, int width, int height,
                              Integer durationSeconds) {
}
