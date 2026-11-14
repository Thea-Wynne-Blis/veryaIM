package com.veryal.common.core;

import com.veryal.common.exception.ErrorCode;
import com.veryal.common.observability.TraceContext;

/**
 * HTTP 统一响应体。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), data, TraceContext.currentTraceId());
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.message());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, TraceContext.currentTraceId());
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, message, null, TraceContext.currentTraceId());
    }

    public boolean successful() {
        return code == ErrorCode.SUCCESS.code();
    }
}
