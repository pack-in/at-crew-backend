package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.analytics.internal.application.AnalyticsInsertId;
import com.atcrew.auth.AuthSignupFailedEvent;
import com.atcrew.auth.AuthSignupSucceededEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * auth 모듈 이벤트를 분석 이벤트로 옮긴다(plans/261005-posthog-analytics-events).
 *
 * <p>패턴(성공/실패 리스너 구분, $insert_id)은 package-info.java 참고.
 */
@Component
class AuthAnalyticsListener {

    private static final String SIGNUP_SUCCEEDED = "auth_signup_succeeded";
    private static final String SIGNUP_FAILED = "auth_signup_failed";

    private final AnalyticsEventPublisher analyticsEventPublisher;

    AuthAnalyticsListener(AnalyticsEventPublisher analyticsEventPublisher) {
        this.analyticsEventPublisher = analyticsEventPublisher;
    }

    /**
     * 회원가입 성공 — 가입 경로와 주 사용 언어를 싣는다. distinct_id는 가입된 회원 ID다.
     */
    @ApplicationModuleListener
    void onAuthSignupSucceeded(AuthSignupSucceededEvent event) {
        String memberId = event.memberId();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("signup_method", event.signupMethod().name());
        properties.put("primary_language", event.primaryLanguage() == null ? null : event.primaryLanguage().name());
        analyticsEventPublisher.publish(SIGNUP_SUCCEEDED, memberId, properties,
                AnalyticsInsertId.of(SIGNUP_SUCCEEDED, memberId));
    }

    /**
     * 회원가입 실패 — 가입 트랜잭션이 롤백돼 아직 회원이 없으므로 회원과 연결되지 않는 임시
     * distinct_id를 발행 시점에 새로 만든다(재시도·레지스트리 경유가 없는 단발성 이벤트라 안전하다).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    void onAuthSignupFailed(AuthSignupFailedEvent event) {
        String distinctId = UUID.randomUUID().toString();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("signup_method", event.signupMethod().name());
        properties.put("error_code", event.errorCode());
        analyticsEventPublisher.publishAnonymous(SIGNUP_FAILED, distinctId, properties,
                AnalyticsInsertId.of(SIGNUP_FAILED, distinctId));
    }
}
