package com.veryal.common.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushCryptoTest {

    /**
     * RFC 5869 Test Case 1（SHA-256），验证 HKDF-Extract + Expand。
     */
    @Test
    void hkdfMatchesRfc5869TestCase1() {
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = HexFormat.of().parseHex("000102030405060708090a0b0c");
        byte[] info = HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9");

        byte[] prk = WebPushCrypto.hmacSha256(salt, ikm);
        assertThat(HexFormat.of().formatHex(prk)).isEqualTo(
                "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5");

        byte[] okm = WebPushCrypto.hkdfSha256(salt, ikm, info, 42);
        assertThat(HexFormat.of().formatHex(okm)).isEqualTo(
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
    }

    /**
     * RFC 8291 加密后按 RFC 8188 布局解析，再用订阅端私钥解密还原明文。
     */
    @Test
    void encryptFollowsRfc8188LayoutAndDecryptsBack() {
        KeyPair receiver = WebPushCrypto.generateEcKeyPair();
        byte[] uaPublic = WebPushCrypto.encodePoint(
                ((java.security.interfaces.ECPublicKey) receiver.getPublic()).getW(),
                WebPushCrypto.ecParameterSpec());
        byte[] authSecret = new byte[16];
        new java.security.SecureRandom().nextBytes(authSecret);

        byte[] plaintext = "{\"title\":\"小明\",\"body\":\"晚上吃饭吗\"}"
                .getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, authSecret);

        // salt(16) + rs(4) + idlen(1) + keyid(65) + ciphertext(plaintext + 0x02 + tag16)
        assertThat(body).hasSize(16 + 4 + 1 + 65 + plaintext.length + 1 + 16);
        assertThat(body[20] & 0xFF).isEqualTo(65);
        assertThat(body[21]).isEqualTo((byte) 0x04);
        assertThat(Arrays.copyOfRange(body, 21, 21 + 65)).hasSize(65);

        int recordSize = ((body[16] & 0xFF) << 24) | ((body[17] & 0xFF) << 16)
                | ((body[18] & 0xFF) << 8) | (body[19] & 0xFF);
        assertThat(recordSize).isEqualTo(WebPushCrypto.RECORD_SIZE);

        byte[] decrypted = WebPushCrypto.decrypt(body, uaPublic,
                (PrivateKey) receiver.getPrivate(), authSecret);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void eachEncryptionUsesFreshEphemeralKeyAndSalt() {
        KeyPair receiver = WebPushCrypto.generateEcKeyPair();
        byte[] uaPublic = WebPushCrypto.encodePoint(
                ((java.security.interfaces.ECPublicKey) receiver.getPublic()).getW(),
                WebPushCrypto.ecParameterSpec());
        byte[] authSecret = new byte[16];
        byte[] plaintext = "hello".getBytes(StandardCharsets.UTF_8);

        byte[] first = WebPushCrypto.encrypt(plaintext, uaPublic, authSecret);
        byte[] second = WebPushCrypto.encrypt(plaintext, uaPublic, authSecret);

        assertThat(first).isNotEqualTo(second);
        assertThat(Arrays.copyOfRange(first, 0, 16))
                .isNotEqualTo(Arrays.copyOfRange(second, 0, 16));
        assertThat(Arrays.copyOfRange(first, 21, 86))
                .isNotEqualTo(Arrays.copyOfRange(second, 21, 86));
    }

    @Test
    void rejectsMalformedPublicKey() {
        byte[] authSecret = new byte[16];
        assertThatThrownBy(() -> WebPushCrypto.encrypt(new byte[]{1}, new byte[10], authSecret))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void base64UrlRoundTrip() {
        byte[] value = Base64.getDecoder().decode("AQIDBA==");
        String encoded = JoseSignatures.base64Url(value);
        assertThat(encoded).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        assertThat(JoseSignatures.base64UrlDecode(encoded)).isEqualTo(value);
    }
}
