package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.auth.AuthSignupFailedEvent;
import com.atcrew.auth.AuthSignupSucceededEvent;
import com.atcrew.member.AuthProvider;
import com.atcrew.member.Language;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * auth 모듈 가입 성공·실패 이벤트를 분석 이벤트로 옮기는 리스너 단위 테스트(PA-03).
 */
class AuthAnalyticsListenerTest {

    AnalyticsEventPublisher analyticsEventPublisher = mock(AnalyticsEventPublisher.class);

    AuthAnalyticsListener listener = new AuthAnalyticsListener(analyticsEventPublisher);

    @Test
    void 가입_성공하면_가입경로와_주사용언어를_실어_회원ID로_발행한다() {
        listener.onAuthSignupSucceeded(new AuthSignupSucceededEvent("member-001", AuthProvider.EMAIL, Language.KO));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("auth_signup_succeeded"), eq("member-001"), properties.capture(),
                eq("auth_signup_succeeded:member-001"), isNull());
        assertThat(properties.getValue())
                .containsEntry("signup_method", "EMAIL")
                .containsEntry("primary_language", "KO");
    }

    @Test
    void 가입_실패하면_가입경로와_에러코드를_실어_임시_식별자로_익명_발행한다() {
        listener.onAuthSignupFailed(new AuthSignupFailedEvent(AuthProvider.GOOGLE, "DUPLICATE_EMAIL"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> distinctId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> insertId = ArgumentCaptor.forClass(String.class);
        verify(analyticsEventPublisher).publishAnonymous(eq("auth_signup_failed"), distinctId.capture(),
                properties.capture(), insertId.capture(), isNull());

        assertThat(properties.getValue())
                .containsEntry("signup_method", "GOOGLE")
                .containsEntry("error_code", "DUPLICATE_EMAIL");
        // memberId가 없는 흐름이라 매 실패마다 새 distinct_id를 발급하고, insert_id도 그 값에 맞춰 짓는다.
        assertThat(distinctId.getValue()).isNotBlank();
        assertThat(insertId.getValue()).isEqualTo("auth_signup_failed:" + distinctId.getValue());
    }
}
