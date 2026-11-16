package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文件临时访问地址。
 *
 * @param url               访问地址；本地存储为相对下载路径
 * @param expiresInSeconds  有效期；0 表示不过期（本地下载接口）
 */
public record FileUrlResponse(
                                 @Schema(description = "可访问地址")
                                 String url,
                                 @Schema(description = "令牌有效期（秒）")
                                 long expiresInSeconds
) {
}
