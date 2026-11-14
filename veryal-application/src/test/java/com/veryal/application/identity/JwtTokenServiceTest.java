package com.veryal.application.identity;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.AuthenticationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef";

    @Test
    void issuesAndParsesToken() {
        TimeProvider time = TimeProvider.fixed(Instant.parse("2026-01-01T00:00:00Z"));
        JwtTokenService service = new JwtTokenService(
                new TokenProperties(SECRET, "veryal", Duration.ofMinutes(10)), time);

        IssuedToken issued = service.issue(1001L, 1L, "device-1");
        AuthenticatedUser user = service.parse(issued.token());

        assertThat(user.userId()).isEqualTo(1001L);
        assertThat(user.tenantId()).isEqualTo(1L);
        assertThat(user.deviceId()).isEqualTo("device-1");
        assertThat(user.tokenId()).isNotBlank();
    }

    @Test
    void rejectsExpiredToken() {
        MutableTimeProvider time = new MutableTimeProvider(Instant.parse("2026-01-01T00:00:00Z"));
        JwtTokenService service = new JwtTokenService(
                new TokenProperties(SECRET, "veryal", Duration.ofMinutes(1)), time);
        IssuedToken issued = service.issue(1001L, 1L, "device-1");

        time.advance(Duration.ofMinutes(2));

        assertThatThrownBy(() -> service.parse(issued.token()))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsTamperedSignature() {
        JwtTokenService service = new JwtTokenService(
                new TokenProperties(SECRET, "veryal", Duration.ofMinutes(10)), TimeProvider.system());
        String token = service.issue(1001L, 1L, "device-1").token();
        String[] parts = token.split("\\.");
        // 改签名段首字符，确保解码后的签名字节一定变化（末尾 base64 字符含无用填充位）。
        char[] signature = parts[2].toCharArray();
        signature[0] = signature[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + new String(signature);

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsTokenSignedByAnotherSecret() {
        JwtTokenService issuer = new JwtTokenService(
                new TokenProperties("another-secret-0123456789abcdefgh", "veryal", Duration.ofMinutes(10)),
                TimeProvider.system());
        JwtTokenService verifier = new JwtTokenService(
                new TokenProperties(SECRET, "veryal", Duration.ofMinutes(10)), TimeProvider.system());
        String token = issuer.issue(1001L, 1L, "device-1").token();

        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsMalformedToken() {
        JwtTokenService service = new JwtTokenService(
                new TokenProperties(SECRET, "veryal", Duration.ofMinutes(10)), TimeProvider.system());
        assertThatThrownBy(() -> service.parse("not-a-jwt")).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> service.parse("")).isInstanceOf(AuthenticationException.class);
    }

    private static final class MutableTimeProvider implements TimeProvider {

        private Instant current;

        private MutableTimeProvider(Instant initial) {
            this.current = initial;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public Instant now() {
            return current;
        }
    }
}
