package com.veryal.common.security;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

/**
 * Web Push 消息加密（RFC 8291 {@code aes128gcm}，编码格式见 RFC 8188）。
 *
 * <p>发送流程：</p>
 * <ol>
 *     <li>生成临时 P-256 密钥对 {@code (as_private, as_public)}；</li>
 *     <li>{@code ecdh_secret = ECDH(as_private, ua_public)}；</li>
 *     <li>{@code ikm = HKDF(auth_secret, ecdh_secret, "WebPush: info\0" || ua_public || as_public, 32)}；</li>
 *     <li>{@code cek = HKDF-Expand(HKDF-Extract(salt, ikm), "Content-Encoding: aes128gcm\0", 16)}；</li>
 *     <li>{@code nonce = HKDF-Expand(prk, "Content-Encoding: nonce\0", 12)}；</li>
 *     <li>AES-128-GCM 加密 {@code plaintext || 0x02}；</li>
 *     <li>组装 {@code salt || rs || idlen || as_public || ciphertext}。</li>
 * </ol>
 *
 * <p>全部使用 JDK 内置 JCE，不引入第三方加密库。</p>
 */
public final class WebPushCrypto {

    /** RFC 8291 规定的记录大小。 */
    public static final int RECORD_SIZE = 4096;

    public static final int PUBLIC_KEY_LENGTH = 65;

    private static final int SALT_LENGTH = 16;
    private static final int TAG_LENGTH_BITS = 128;
    private static final byte PADDING_DELIMITER = 0x02;

    private static final byte[] AUTH_INFO_PREFIX =
            "WebPush: info\u0000".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO =
            "Content-Encoding: aes128gcm\u0000".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO =
            "Content-Encoding: nonce\u0000".getBytes(StandardCharsets.US_ASCII);

    private static final String CURVE = "secp256r1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    /**
     * 使用随机盐与随机临时密钥加密。
     */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        return encrypt(plaintext, uaPublic, authSecret, randomBytes(SALT_LENGTH),
                generateEcKeyPair());
    }

    /**
     * 指定盐与临时密钥加密，便于用 RFC 测试向量验证。
     */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, byte[] salt,
                                 KeyPair ephemeral) {
        try {
            ECParameterSpec spec = ecParameterSpec();
            byte[] asPublic = encodePoint(((ECPublicKey) ephemeral.getPublic()).getW(), spec);
            byte[] ecdhSecret = ecdh(ephemeral.getPrivate(), decodePoint(uaPublic, spec), spec);

            byte[] keyInfo = concat(AUTH_INFO_PREFIX, uaPublic, asPublic);
            byte[] ikm = hkdfSha256(authSecret, ecdhSecret, keyInfo, 32);

            byte[] prk = hmacSha256(salt, ikm);
            byte[] cek = hkdfExpand(prk, CEK_INFO, 16);
            byte[] nonce = hkdfExpand(prk, NONCE_INFO, 12);

            byte[] record = concat(plaintext, new byte[]{PADDING_DELIMITER});
            byte[] ciphertext = aesGcm(Cipher.ENCRYPT_MODE, cek, nonce, record);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(salt);
            out.write(intToBytes(RECORD_SIZE));
            out.write(PUBLIC_KEY_LENGTH);
            out.write(asPublic);
            out.write(ciphertext);
            return out.toByteArray();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("web push encryption failed", e);
        }
    }

    /**
     * 解密 RFC 8188 请求体。仅用于测试与排障，生产环境只需加密。
     *
     * @param body       加密后的请求体
     * @param uaPublic   订阅端公钥（65 字节未压缩点）
     * @param uaPrivate  订阅端私钥
     * @param authSecret 订阅端 auth secret
     */
    public static byte[] decrypt(byte[] body, byte[] uaPublic, PrivateKey uaPrivate,
                                 byte[] authSecret) {
        try {
            if (body.length < SALT_LENGTH + 5 + PUBLIC_KEY_LENGTH) {
                throw new IllegalArgumentException("encrypted body too short");
            }
            byte[] salt = Arrays.copyOfRange(body, 0, SALT_LENGTH);
            int idLen = body[SALT_LENGTH + 4] & 0xFF;
            int keyIdOffset = SALT_LENGTH + 5;
            byte[] asPublic = Arrays.copyOfRange(body, keyIdOffset, keyIdOffset + idLen);
            byte[] ciphertext = Arrays.copyOfRange(body, keyIdOffset + idLen, body.length);

            ECParameterSpec spec = ecParameterSpec();
            byte[] ecdhSecret = ecdh(uaPrivate, decodePoint(asPublic, spec), spec);
            byte[] keyInfo = concat(AUTH_INFO_PREFIX, uaPublic, asPublic);
            byte[] ikm = hkdfSha256(authSecret, ecdhSecret, keyInfo, 32);
            byte[] prk = hmacSha256(salt, ikm);
            byte[] cek = hkdfExpand(prk, CEK_INFO, 16);
            byte[] nonce = hkdfExpand(prk, NONCE_INFO, 12);
            return stripPadding(aesGcm(Cipher.DECRYPT_MODE, cek, nonce, ciphertext));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("web push decryption failed", e);
        }
    }

    /**
     * HKDF-SHA256（RFC 5869）：Extract + Expand。
     */
    public static byte[] hkdfSha256(byte[] salt, byte[] ikm, byte[] info, int length) {
        return hkdfExpand(hmacSha256(salt, ikm), info, length);
    }

    static byte[] hkdfExpand(byte[] prk, byte[] info, int length) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] previous = new byte[0];
            int counter = 1;
            while (out.size() < length) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));
                mac.update(previous);
                mac.update(info);
                mac.update((byte) counter);
                previous = mac.doFinal();
                out.write(previous, 0, Math.min(previous.length, length - out.size()));
                counter++;
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("hkdf expand failed", e);
        }
    }

    static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            // HKDF 允许空盐，等价于 HashLen 个 0
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[32] : key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("hmac failed", e);
        }
    }

    public static KeyPair generateEcKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("failed to generate EC key pair", e);
        }
    }

    public static ECParameterSpec ecParameterSpec() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(CURVE));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load P-256 parameters", e);
        }
    }

    /**
     * 65 字节未压缩点 → EC 公钥。
     */
    public static java.security.PublicKey publicKeyFrom(byte[] uncompressed) {
        try {
            return KeyFactory.getInstance("EC").generatePublic(
                    new ECPublicKeySpec(decodePoint(uncompressed, ecParameterSpec()),
                            ecParameterSpec()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to build EC public key", e);
        }
    }

    /**
     * 32 字节私钥标量 → EC 私钥。
     */
    public static PrivateKey privateKeyFrom(byte[] scalar) {
        try {
            return KeyFactory.getInstance("EC").generatePrivate(
                    new ECPrivateKeySpec(new BigInteger(1, scalar), ecParameterSpec()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to build EC private key", e);
        }
    }

    public static byte[] encodePoint(ECPoint point, ECParameterSpec spec) {
        BigInteger p = ((ECFieldFp) spec.getCurve().getField()).getP();
        int size = (p.bitLength() + 7) / 8;
        byte[] encoded = new byte[1 + size * 2];
        encoded[0] = 0x04;
        copyFixed(point.getAffineX(), encoded, 1, size);
        copyFixed(point.getAffineY(), encoded, 1 + size, size);
        return encoded;
    }

    private static ECPoint decodePoint(byte[] encoded, ECParameterSpec spec) {
        if (encoded == null || encoded.length != PUBLIC_KEY_LENGTH || encoded[0] != 0x04) {
            throw new IllegalArgumentException("invalid uncompressed EC point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(encoded, 33, 65));
        return new ECPoint(x, y);
    }

    private static byte[] ecdh(PrivateKey privateKey, ECPoint publicPoint, ECParameterSpec spec)
            throws Exception {
        java.security.PublicKey publicKey = KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(publicPoint, spec));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    private static byte[] aesGcm(int mode, byte[] key, byte[] nonce, byte[] input)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        return cipher.doFinal(input);
    }

    private static byte[] stripPadding(byte[] record) {
        int end = record.length;
        while (end > 0 && record[end - 1] == 0) {
            end--;
        }
        if (end > 0 && record[end - 1] == PADDING_DELIMITER) {
            end--;
        }
        return Arrays.copyOfRange(record, 0, end);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8),
                (byte) value};
    }

    private static void copyFixed(BigInteger value, byte[] target, int offset, int size) {
        byte[] bytes = value.toByteArray();
        int copy = Math.min(bytes.length, size);
        System.arraycopy(bytes, bytes.length - copy, target, offset + size - copy, copy);
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
