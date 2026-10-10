package com.atcrew.analytics.internal.config;

import com.posthog.server.PostHog;
import com.posthog.server.PostHogConfig;
import com.posthog.server.PostHogInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PostHog 클라이언트 빈 등록(plans/261005-posthog-analytics-events).
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

    @Bean(destroyMethod = "close")
    PostHogInterface postHogClient(PostHogProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("posthog.api-key가 비어 있다 — 분석 이벤트를 전송할 수 없다.");
        }
        PostHogConfig config = PostHogConfig.builder(properties.apiKey())
                .host(properties.host())
                // 기능 플래그는 쓰지 않는다. 켜 두면 기동 시점과 주기마다 플래그 정의를 받아오는 호출만 늘어난다.
                .preloadFeatureFlags(false)
                .build();
        log.info("PostHog 초기화 완료: host={}", config.getHost());
        return PostHog.with(config);
    }
}
