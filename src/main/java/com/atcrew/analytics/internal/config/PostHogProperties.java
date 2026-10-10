package com.atcrew.analytics.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PostHog 전송 설정(plans/261005-posthog-analytics-events).
 *
 * @param apiKey 프로젝트 API 키. 비어 있으면 기동하지 않는다 — {@link PostHogClientConfig} 참고
 * @param host   수집 엔드포인트. 기본값은 US 클라우드
 */
@ConfigurationProperties(prefix = "posthog")
record PostHogProperties(String apiKey, String host) {
}
