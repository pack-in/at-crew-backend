package com.atcrew.auth;

import com.atcrew.member.AuthProvider;

/**
 * 회원가입 실패 시 발행되는 이벤트. analytics 모듈이 구독해 PostHog로 전송한다
 * (plans/261005-posthog-analytics-events PA-03).
 *
 * <p>가입 트랜잭션이 롤백돼 아직 회원이 생성되지 않았으므로 memberId를 싣지 않는다.
 */
public record AuthSignupFailedEvent(
        AuthProvider signupMethod, // 시도한 가입 경로(EMAIL | GOOGLE)
        String errorCode           // 실패 원인 에러코드(DomainException#getCode, 식별 불가 시 "UNKNOWN")
) {
}
