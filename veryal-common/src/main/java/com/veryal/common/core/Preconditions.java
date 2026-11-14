package com.veryal.common.core;

import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.VeryalException;

/**
 * 轻量参数校验工具，避免引入额外依赖。
 */
public final class Preconditions {

    private Preconditions() {
    }

    public static void check(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new VeryalException(errorCode, message);
        }
    }

    public static <T> T notNull(T value, ErrorCode errorCode, String message) {
        if (value == null) {
            throw new VeryalException(errorCode, message);
        }
        return value;
    }

    public static String notBlank(String value, ErrorCode errorCode, String message) {
        if (value == null || value.isBlank()) {
            throw new VeryalException(errorCode, message);
        }
        return value;
    }

    public static String lengthBetween(String value, int min, int max, ErrorCode errorCode, String message) {
        notBlank(value, errorCode, message);
        if (value.length() < min || value.length() > max) {
            throw new VeryalException(errorCode, message);
        }
        return value;
    }

    public static int intRange(int value, int min, int max, ErrorCode errorCode, String message) {
        if (value < min || value > max) {
            throw new VeryalException(errorCode, message);
        }
        return value;
    }

    public static long positive(long value, ErrorCode errorCode, String message) {
        if (value <= 0) {
            throw new VeryalException(errorCode, message);
        }
        return value;
    }
}
