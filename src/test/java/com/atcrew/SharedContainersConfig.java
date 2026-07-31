package com.atcrew;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * 테스트 전용 MongoDB/MariaDB 컨테이너 공유 설정.
 *
 * <p>{@code @ImportTestcontainers}로 임포트하면 컨테이너가 Spring ApplicationContext 빈으로
 * 등록되어 라이프사이클이 JUnit 테스트 클래스가 아닌 ApplicationContext에 묶인다.
 * Spring 컨텍스트 캐싱 중에는 컨테이너가 중단되지 않고, 컨텍스트 소멸 시 함께 종료된다.
 */
public class SharedContainersConfig {

    @Container
    @ServiceConnection
    public static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    // recruit 모듈부터 도입된 JPA/MariaDB 영속성 계층 — @ServiceConnection이 spring.datasource.* 를 자동 구성
    @Container
    @ServiceConnection
    public static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");
}
