package com.atcrew.common.observability;

import ch.qos.logback.classic.LoggerContext;
import com.atcrew.SharedContainersConfig;
import io.sentry.Sentry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.util.Spliterators;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DSN이 실제로 주입된 상태에서 애플리케이션 컨텍스트가 뜨는지 검증한다.
 *
 * <p><b>왜 필요한가.</b> 이슈 #62에서 Sentry 스타터가 Spring Boot 4와 비호환이라 prod 기동이
 * 실패했는데 CI는 계속 초록이었다. 초기화가 {@code sentry.dsn}이 있을 때만 도는데 테스트 리소스에
 * DSN이 없었기 때문이다 — DSN이 주입되는 경로 자체가 한 번도 테스트되지 않았다.
 * 지금은 {@code src/test/resources/application.yml}이 더미 DSN을 주므로 모든 통합 테스트가 그 경로를
 * 밟고, 이 클래스는 초기화 결과를 명시적으로 확인한다.
 *
 * <p><b>속성 오버라이드를 두지 않는 이유.</b> {@code @SpringBootTest(properties=...)}로 값을 주면
 * 컨텍스트 캐시 키가 달라져 컨텍스트가 하나 더 생긴다. 그 컨텍스트가 닫힐 때
 * {@link SharedContainersConfig}의 공유 컨테이너가 함께 종료돼 다른 테스트가 연쇄로 깨진다
 * (2026-08-27 실측 — Elasticsearch 연결 거부로 검색·이벤트 레지스트리 테스트가 무너졌다).
 * 설정은 반드시 테스트 {@code application.yml}에 둔다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ImportTestcontainers(SharedContainersConfig.class)
@DisplayName("Sentry 초기화 통합 테스트")
class SentryInitializationTest {

    @Test
    @DisplayName("DSN이 주입돼도 컨텍스트가 정상 기동하고 SDK가 활성화된다 (이슈 #62 회귀 방지)")
    void contextLoadsAndSentryIsEnabled() {
        assertThat(Sentry.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("루트 로거에 Sentry 어펜더가 정확히 하나만 붙는다")
    void appenderIsAttachedExactlyOnce() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);

        assertThat(root.getAppender(SentryConfig.APPENDER_NAME)).isNotNull();

        // logback의 LoggerContext는 JVM 전역인데 초기화는 Spring 컨텍스트마다 돈다.
        // 컨텍스트가 여럿 뜨는 테스트 JVM에서 어펜더가 쌓이면 ERROR 로그가 중복 전송되고
        // 전송 큐·스레드가 컨텍스트 수만큼 늘어난다(2026-08-27 실제로 그렇게 만들었다).
        long sentryAppenders = StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(root.iteratorForAppenders(), 0), false)
                .filter(a -> SentryConfig.APPENDER_NAME.equals(a.getName()))
                .count();
        assertThat(sentryAppenders).isEqualTo(1);
    }
}
