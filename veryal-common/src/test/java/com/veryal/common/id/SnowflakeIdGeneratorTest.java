package com.veryal.common.id;

import com.veryal.common.exception.SystemException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesUniqueAndStrictlyIncreasingIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7);
        Set<Long> ids = new HashSet<>();
        long previous = -1L;
        for (int i = 0; i < 20_000; i++) {
            long id = generator.nextId();
            assertThat(id).isGreaterThan(previous);
            previous = id;
            assertThat(ids.add(id)).isTrue();
        }
        assertThat(ids).hasSize(20_000);
    }

    @Test
    void differentNodesProduceDifferentIds() {
        SnowflakeIdGenerator nodeOne = new SnowflakeIdGenerator(1);
        SnowflakeIdGenerator nodeTwo = new SnowflakeIdGenerator(2);
        assertThat(nodeOne.nextId()).isNotEqualTo(nodeTwo.nextId());
    }

    @Test
    void rejectsNodeIdOutOfRange() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024)).isInstanceOf(SystemException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1)).isInstanceOf(SystemException.class);
    }
}
