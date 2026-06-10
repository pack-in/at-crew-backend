package com.atcrew.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

// 멀티 도큐먼트 트랜잭션 지원 — MongoDB 레플리카셋 환경에서만 활성화
// application-prod.yml: spring.data.mongodb.transactions.enabled: true
@Configuration
@ConditionalOnProperty(name = "spring.data.mongodb.transactions.enabled", havingValue = "true")
class MongoConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
