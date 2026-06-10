package com.atcrew;

import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

@TestConfiguration
public class TestMongoConfig {

    // 테스트 종료 후 컨테이너가 먼저 내려가면 destroy 대기시간이 30초 → 2초로 단축
    @Bean
    MongoClientSettingsBuilderCustomizer shortServerSelectionTimeout() {
        return builder -> builder.applyToClusterSettings(
                s -> s.serverSelectionTimeout(2, TimeUnit.SECONDS));
    }
}
