package com.veryal.common.exception;

/**
 * 统一错误码。
 *
 * <p>错误码分段规则：</p>
 * <ul>
 *     <li>0：成功</li>
 *     <li>4xxxx：客户端 / 业务错误</li>
 *     <li>5xxxx：服务端 / 系统错误</li>
 * </ul>
 */
public enum ErrorCode {

    SUCCESS(0, "success", 200),

    BAD_REQUEST(40000, "请求参数不合法", 400),
    INVALID_ARGUMENT(40001, "参数校验失败", 400),
    MESSAGE_CONTENT_INVALID(40002, "消息内容不合法", 400),
    FRAME_TOO_LARGE(40003, "消息帧超过最大长度限制", 400),
    FILE_TOO_LARGE(40004, "文件超过大小限制", 400),

    UNAUTHORIZED(40100, "未认证或认证信息缺失", 401),
    TOKEN_EXPIRED(40101, "登录凭证已过期", 401),
    TOKEN_INVALID(40102, "登录凭证无效", 401),
    AUTHENTICATION_FAILED(40103, "用户名或密码错误", 401),

    FORBIDDEN(40300, "无权执行该操作", 403),
    NOT_CONVERSATION_MEMBER(40301, "不是该会话成员", 403),
    CONVERSATION_TYPE_NOT_SUPPORTED(40302, "不支持的会话类型", 403),

    NOT_FOUND(40400, "资源不存在", 404),
    USER_NOT_FOUND(40401, "用户不存在", 404),
    CONVERSATION_NOT_FOUND(40402, "会话不存在", 404),
    MESSAGE_NOT_FOUND(40403, "消息不存在", 404),
    DEVICE_NOT_FOUND(40404, "设备不存在", 404),
    FILE_NOT_FOUND(40405, "文件不存在", 404),
    TENANT_NOT_FOUND(40406, "租户不存在", 404),
    FRIEND_REQUEST_NOT_FOUND(40407, "好友申请不存在", 404),

    FRIEND_REQUEST_EXISTS(40904, "好友申请已存在", 409),
    ALREADY_FRIEND(40905, "已经是好友", 409),

    TENANT_DISABLED(40303, "租户已停用", 403),
    TENANT_QUOTA_EXCEEDED(40304, "租户配额不足", 403),
    USER_BLOCKED(40305, "对方已拒收消息", 403),

    CONFLICT(40900, "资源状态冲突", 409),
    USERNAME_ALREADY_EXISTS(40901, "用户名已存在", 409),
    DUPLICATE_MESSAGE(40902, "重复消息", 409),
    DUPLICATE_REQUEST(40903, "重复请求", 409),

    RATE_LIMITED(42900, "请求过于频繁", 429),

    INTERNAL_ERROR(50000, "服务内部错误", 500),
    PERSISTENCE_ERROR(50010, "数据持久化失败", 500),
    CONFIGURATION_ERROR(50011, "配置错误", 500),
    STORAGE_ERROR(50012, "文件存储失败", 500),
    PROTOCOL_ERROR(50020, "协议解析失败", 400),
    INVALID_FRAME(50021, "非法协议帧", 400),
    UNSUPPORTED_COMMAND(50022, "不支持的协议指令", 400),
    UNSUPPORTED_MESSAGE_TYPE(50023, "不支持的消息类型", 400),
    SERVICE_UNAVAILABLE(50300, "服务暂不可用", 503);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
