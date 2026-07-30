package com.atcrew;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// MariaDB 전환(docs/design/mariadb-migration-design.md) P1~P4 — Mongo와 JPA 감사(auditing)를 병행 지원.
// P5에서 Mongo 의존성 제거 시 @EnableMongoAuditing도 함께 삭제.
@SpringBootApplication
@EnableMongoAuditing
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class AtCrewBackendApplication {

	public static void main(String[] args) {
		// 배포 환경의 OS/컨테이너 기본 시간대에 암묵적으로 의존하지 않도록 JVM 시작 시 명시 고정.
		// 로깅 타임스탬프·향후 LocalDate/LocalDateTime.now() 호출 전부에 적용된다.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(AtCrewBackendApplication.class, args);
	}

}
