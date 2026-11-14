package com.veryal.common.exception;

/**
 * 系统异常：基础设施、配置或不可恢复的运行期故障。
 */
public class SystemException extends VeryalException {

    public SystemException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SystemException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SystemException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
