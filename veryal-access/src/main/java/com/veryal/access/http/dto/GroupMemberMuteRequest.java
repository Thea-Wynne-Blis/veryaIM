package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 成员禁言请求。
 *
 * <p>档位固定三档：{@code MUTE_7M}、{@code MUTE_30M}、{@code MUTE_FOREVER}；
 * 解除禁言传 {@code UNMUTE}。</p>
 */
public record GroupMemberMuteRequest(
        @Schema(description = "禁言档位：MUTE_7M / MUTE_30M / MUTE_FOREVER / UNMUTE",
                example = "MUTE_30M")
        @NotBlank String duration
) {
}
