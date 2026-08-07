package com.atcrew;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
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
