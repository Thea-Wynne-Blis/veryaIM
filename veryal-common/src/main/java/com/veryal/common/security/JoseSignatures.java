package com.veryal.common.security;

import java.math.BigInteger;
import java.util.Base64;

/**
 * JOSE（JWS）签名与密钥编码工具。
 *
 * <p>Java 的 ECDSA 签名结果是 DER 序列（SEQUENCE of R, S），而 JOSE 要求定长
 * {@code R || S}（P-256 各 32 字节）。此处负责转换与 base64url 编码。</p>
 */
public final class JoseSignatures {

    private static final int P256_COORDINATE_BYTES = 32;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private JoseSignatures() {
    }

    public static String base64Url(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    public static byte[] base64UrlDecode(String value) {
        return URL_DECODER.decode(value);
    }

    /**
     * DER(SEQUENCE{R,S}) → JOSE 定长 R||S。
     */
    public static byte[] derToJose(byte[] der) {
        int offset = 2;
        // 长格式长度（>127）多一个字节
        if ((der[1] & 0xFF) > 0x7F) {
            offset = 3;
        }
        int rLength = der[offset + 1] & 0xFF;
        int rOffset = offset + 2;
        int sLength = der[rOffset + rLength + 1] & 0xFF;
        int sOffset = rOffset + rLength + 2;

        byte[] result = new byte[P256_COORDINATE_BYTES * 2];
        copyRightAligned(der, rOffset, rLength, result, 0);
        copyRightAligned(der, sOffset, sLength, result, P256_COORDINATE_BYTES);
        return result;
    }

    /**
     * 无符号大端字节 → BigInteger，用于从 65 字节未压缩点还原密钥。
     */
    public static BigInteger unsigned(byte[] value) {
        return new BigInteger(1, value);
    }

    private static void copyRightAligned(byte[] source, int offset, int length, byte[] target,
                                         int targetOffset) {
        int copy = Math.min(length, P256_COORDINATE_BYTES);
        int sourceStart = offset + length - copy;
        int targetStart = targetOffset + P256_COORDINATE_BYTES - copy;
        System.arraycopy(source, sourceStart, target, targetStart, copy);
    }
}
