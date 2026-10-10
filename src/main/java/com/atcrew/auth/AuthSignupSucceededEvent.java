package com.atcrew.auth;

import com.atcrew.member.AuthProvider;
import com.atcrew.member.Language;

/**
 * 회원가입 성공 시 발행되는 이벤트. analytics 모듈이 구독해 PostHog로 전송한다
 * (plans/261005-posthog-analytics-events PA-03).
 *
 * <p>{@code account_type} 공통 속성은 PH-02 회신 전까지 생략한다 — 이 이벤트에도 포함하지 않는다.
 */
public record AuthSignupSucceededEvent(
        String memberId,           // 가입된 회원 ID — PostHog distinct_id로 쓰인다
        AuthProvider signupMethod, // 가입 경로(EMAIL | GOOGLE)
        Language primaryLanguage   // 가입 시 선택한 주 사용 언어
) {
}
