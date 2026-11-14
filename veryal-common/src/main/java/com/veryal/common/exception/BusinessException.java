package com.veryal.common.exception;

/**
 * 业务规则异常：由可预期的业务条件触发，例如用户名冲突、非会话成员。
 */
public class BusinessException extends VeryalException {

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
