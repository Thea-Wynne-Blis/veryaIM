package com.veryal.domain.identity;

/**
 * 密码哈希端口。算法实现放在应用层，领域只关心能力。
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
