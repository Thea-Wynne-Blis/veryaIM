package com.veryal.application.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void hashesAndVerifiesPassword() {
        String hash = hasher.hash("correct horse battery staple");
        assertThat(hash).startsWith("pbkdf2_sha256$");
        assertThat(hasher.matches("correct horse battery staple", hash)).isTrue();
        assertThat(hasher.matches("wrong password", hash)).isFalse();
    }

    @Test
    void producesDifferentSaltForSamePassword() {
        String first = hasher.hash("same-password");
        String second = hasher.hash("same-password");
        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.matches("same-password", second)).isTrue();
    }

    @Test
    void rejectsMalformedEncodedPassword() {
        assertThat(hasher.matches("password", "plain-text")).isFalse();
        assertThat(hasher.matches("password", null)).isFalse();
    }
}
