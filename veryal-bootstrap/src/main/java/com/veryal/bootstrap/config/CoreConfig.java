package com.veryal.bootstrap.config;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.id.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 核心基础设施 Bean。
 */
@Configuration
public class CoreConfig {

    @Bean
    public TimeProvider timeProvider() {
        return TimeProvider.system();
    }

    /**
     * node-id 在集群内必须唯一，否则雪花 ID 可能冲突。
     */
    @Bean
    public IdGenerator idGenerator(@Value("${veryal.node-id:1}") long nodeId) {
        return new SnowflakeIdGenerator(nodeId);
    }
}
