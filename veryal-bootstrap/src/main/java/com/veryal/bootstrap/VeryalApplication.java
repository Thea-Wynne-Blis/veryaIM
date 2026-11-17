package com.veryal.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VERYAL 启动入口。
 *
 * <p>采用模块化单体部署：同一进程内装配 HTTP API、WebSocket Gateway、Outbox 发布器与投递消费者。</p>
 */
@SpringBootApplication(scanBasePackages = "com.veryal")
@ConfigurationPropertiesScan("com.veryal")
@EnableScheduling
public class VeryalApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeryalApplication.class, args);
    }
}
