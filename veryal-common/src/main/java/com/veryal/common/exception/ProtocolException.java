package com.veryal.common.exception;

/**
 * 协议异常：帧格式错误、未知指令、Envelope 解析失败等。
 *
 * <p>协议异常一旦发生，接入层应记录日志并关闭连接，避免协议状态不一致。</p>
 */
public class ProtocolException extends VeryalException {

    public ProtocolException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ProtocolException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ProtocolException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
