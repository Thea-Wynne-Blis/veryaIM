package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 表情包搜索结果。
 *
 * @param keyword 实际使用的关键词
 * @param page    页码，从 1 开始
 * @param items   图片地址列表，前端直接作为缩略图展示
 */
public record StickerSearchResponse(
        @Schema(description = "搜索关键词")
        String keyword,
        @Schema(description = "页码，从 1 开始")
        int page,
        @Schema(description = "表情图片地址；无结果时为空数组")
        List<String> items
) {
}
