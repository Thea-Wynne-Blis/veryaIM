package com.veryal.common.exception;

/**
 * 认证异常：Token 缺失、过期、签名错误或设备身份不合法。
 */
public class AuthenticationException extends VeryalException {

    public AuthenticationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AuthenticationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
