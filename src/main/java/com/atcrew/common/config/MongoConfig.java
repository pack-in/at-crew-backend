package com.atcrew.common.config;

import com.mongodb.MongoClientSettings;
import org.bson.UuidRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

@Configuration
class MongoConfig {

    // Spring Modulith 이벤트 발행 ID(UUID) 인코딩을 위해 표준 UUID 표현 방식 설정
    @Bean
    MongoClientSettingsBuilderCustomizer uuidRepresentationCustomizer() {
        return builder -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }

    // 멀티 도큐먼트 트랜잭션 지원 — MongoDB 레플리카셋 환경에서만 활성화
    // application-prod.yml: spring.data.mongodb.transactions.enabled: true
    @Bean
    @ConditionalOnProperty(name = "spring.data.mongodb.transactions.enabled", havingValue = "true")
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
