package com.veryal.common.exception;

/**
 * 持久化异常：数据库访问失败、唯一键冲突、乐观锁失败等。
 */
public class PersistenceException extends VeryalException {

    public PersistenceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PersistenceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public PersistenceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
