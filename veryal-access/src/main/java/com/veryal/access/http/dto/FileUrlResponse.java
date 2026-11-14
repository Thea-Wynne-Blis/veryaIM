package com.veryal.access.http.dto;

/**
 * 文件临时访问地址。
 *
 * @param url               访问地址；本地存储为相对下载路径
 * @param expiresInSeconds  有效期；0 表示不过期（本地下载接口）
 */
public record FileUrlResponse(String url, long expiresInSeconds) {
}
