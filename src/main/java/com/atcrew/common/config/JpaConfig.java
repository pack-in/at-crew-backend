package com.atcrew.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// MariaDB 전환(docs/design/mariadb-migration-design.md) P1 — recruit 모듈부터 JPA 리포지토리 사용
@Configuration
@EnableJpaRepositories(basePackages = "com.atcrew")
@EnableJpaAuditing
class JpaConfig {
}
