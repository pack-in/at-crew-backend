package com.atcrew;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;

/**
 * 테스트 전용 컨테이너 공유 설정.
 *
 * <p>{@code @ImportTestcontainers}로 임포트하면 컨테이너가 Spring ApplicationContext 빈으로
 * 등록되어 라이프사이클이 JUnit 테스트 클래스가 아닌 ApplicationContext에 묶인다.
 * Spring 컨텍스트 캐싱 중에는 컨테이너가 중단되지 않고, 컨텍스트 소멸 시 함께 종료된다.
 *
 * <p>ElasticsearchContainer는 검색 모듈(docs/design/search-module-design.md)의 색인 대상 컨테이너다.
 */
public class SharedContainersConfig {

    @Container
    @ServiceConnection
    public static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    @ServiceConnection
    public static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:9.2.8")
            .withEnv("xpack.security.enabled", "false")
            // 기본 시작 대기 타임아웃이 이 환경에서 ES 완전 기동에 부족해 늘림 (ContainerLaunchException 방지)
            .withStartupTimeout(Duration.ofMinutes(3));
}
