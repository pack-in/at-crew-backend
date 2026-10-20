package com.atcrew.analytics.internal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * PostHog 전송 클라이언트 빈 등록(plans/261005-posthog-analytics-events, 261019-posthog-server-delivery).
 *
 * <p>API 키는 기본값을 두지 않는다 — 값이 비면 기동하지 않는다. 다른 필수 시크릿(jwt.secret,
 * media.key-signature.secret)과 같은 원칙이다: 공개 레포에 적은 기본값은 곧 공개된 시크릿이고,
 * 값 주입을 빠뜨린 배포가 분석이 꺼진 채로 조용히 뜨는 상태를 만들지 않는다.
 * 로컬은 application-local.yml이, 테스트는 test classpath의 application.yml이 값을 준다.
 */
@Configuration
@EnableConfigurationProperties(PostHogProperties.class)
class PostHogClientConfig {

    private static final Logger log = LoggerFactory.getLogger(PostHogClientConfig.class);

    // 전송은 리스너 스레드에서 동기로 기다린다. 그 스레드는 다른 모듈의 비동기 리스너와 함께 쓰는
    // applicationTaskExecutor(기본 8개)라, PostHog가 느릴 때 오래 붙잡지 않도록 짧게 둔다.
    // 시간 초과로 실패한 이벤트는 발행 기록이 남아 재기동 때 다시 보낸다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    PostHogCaptureClient postHogCaptureClient(PostHogProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("posthog.api-key가 비어 있다 — 분석 이벤트를 전송할 수 없다.");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.host())
                .requestFactory(requestFactory)
                .build();
        log.info("PostHog 초기화 완료: host={}", properties.host());
        return new PostHogCaptureClient(restClient, properties.apiKey());
    }
}
