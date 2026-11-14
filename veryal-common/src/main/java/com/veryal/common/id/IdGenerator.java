package com.veryal.common.id;

/**
 * 业务主键生成器。不同 ID 语义独立，禁止混用。
 */
public interface IdGenerator {

    long nextId();

    default String nextIdAsString() {
        return Long.toString(nextId());
    }
}
