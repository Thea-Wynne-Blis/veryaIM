package com.veryal.common.core;

import cn.hutool.core.util.IdUtil;

/**
 * 随机标识工具。统一基于 Hutool 生成，避免各处重复实现。
 */
public final class RandomIds {

    private RandomIds() {
    }

    /**
     * 32 位无连字符 UUID，用于 traceId、connectionId、eventId、jti 等。
     */
    public static String newId() {
        return IdUtil.fastSimpleUUID();
    }
}
