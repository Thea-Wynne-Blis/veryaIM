package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 运营操作附言。
 */
public record OpsActionRequest(
                                 @Schema(description = "原因说明")
                                 String reason
) {
}
