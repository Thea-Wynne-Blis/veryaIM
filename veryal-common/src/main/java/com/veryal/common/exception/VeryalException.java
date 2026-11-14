package com.veryal.common.exception;

/**
 * VERYAL 所有受控异常的基类。
 */
public class VeryalException extends RuntimeException {

    private final ErrorCode errorCode;

    public VeryalException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public VeryalException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public VeryalException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
