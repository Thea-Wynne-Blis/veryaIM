package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * 阅读回执请求。
 */
public record ReadRequest(
                                 @Schema(description = "已读到的消息序号")
                                 @Min(0) long readSequence
) {
}
