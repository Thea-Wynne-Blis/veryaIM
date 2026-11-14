package com.veryal.protocol.api;

/**
 * VERYAL 传输协议常量。
 *
 * <p>固定 16 字节协议头，Big Endian：</p>
 * <pre>
 * | Magic(4) | Version(1) | Flags(1) | Command(2) | Sequence(4) | BodyLength(4) | Payload(N) |
 * </pre>
 */
public final class ProtocolConstants {

    /** 魔数 "VRYL" */
    public static final int MAGIC = 0x5652594C;

    public static final int PROTOCOL_VERSION = 1;

    public static final int HEADER_LENGTH = 16;

    public static final int DEFAULT_MAX_FRAME_LENGTH = 1024 * 1024;

    /** 消息体使用 Protobuf 编码。 */
    public static final int FLAG_PROTOBUF = 0x01;

    public static final int FLAG_NONE = 0x00;

    private ProtocolConstants() {
    }
}
