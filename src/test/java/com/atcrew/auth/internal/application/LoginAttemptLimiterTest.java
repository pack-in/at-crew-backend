package com.atcrew.auth.internal.application;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.auth.internal.persistence.LoginAttemptRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LoginAttemptLimiterTest {

    static final String EMAIL = "user@test.com";
    static final String IP = "203.0.113.7";
    static final String EMAIL_KEY = "email:" + EMAIL;
    static final String IP_KEY = "ip:" + IP;

    LoginAttemptRepository loginAttemptRepository;
    LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        loginAttemptRepository = mock(LoginAttemptRepository.class);
        limiter = new LoginAttemptLimiter(loginAttemptRepository);

        when(loginAttemptRepository.findFailCountWithinWindow(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        // extractIp()가 신뢰하는 X-Real-IP를 채워 요청 컨텍스트를 준비한다
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", IP);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ─── IP 전용 경로 ─────────────────────────────────────────────────

    @Test
    void IP_실패가_임계값_이상이면_checkIpBlocked가_429() {
        when(loginAttemptRepository.findFailCountWithinWindow(eq(IP_KEY), any(Instant.class)))
                .thenReturn(Optional.of(30));

        assertThatThrownBy(() -> limiter.checkIpBlocked())
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.TOO_MANY_ATTEMPTS.name()));
    }

    @Test
    void IP_실패가_임계값_미만이면_checkIpBlocked_통과() {
        when(loginAttemptRepository.findFailCountWithinWindow(eq(IP_KEY), any(Instant.class)))
                .thenReturn(Optional.of(29));

        assertThatCode(() -> limiter.checkIpBlocked()).doesNotThrowAnyException();
    }

    @Test
    void checkIpBlocked는_email_카운터를_조회하지_않는다() {
        limiter.checkIpBlocked();

        verify(loginAttemptRepository).findFailCountWithinWindow(eq(IP_KEY), any(Instant.class));
        verify(loginAttemptRepository, never()).findFailCountWithinWindow(eq(EMAIL_KEY), any(Instant.class));
    }

    @Test
    void recordIpFailure는_ip_카운터만_증가시킨다() {
        limiter.recordIpFailure();

        verify(loginAttemptRepository).increment(eq(IP_KEY), any(Instant.class), any(Instant.class));
        verify(loginAttemptRepository, never()).increment(eq(EMAIL_KEY), any(Instant.class), any(Instant.class));
    }

    // ─── 이메일 경로 (기존 동작 유지) ──────────────────────────────────

    @Test
    void email_실패가_임계값_이상이면_checkBlocked가_429() {
        when(loginAttemptRepository.findFailCountWithinWindow(eq(EMAIL_KEY), any(Instant.class)))
                .thenReturn(Optional.of(5));

        assertThatThrownBy(() -> limiter.checkBlocked(EMAIL))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.TOO_MANY_ATTEMPTS.name()));
    }

    @Test
    void email이_한도_내여도_IP가_초과면_checkBlocked가_429() {
        when(loginAttemptRepository.findFailCountWithinWindow(eq(EMAIL_KEY), any(Instant.class)))
                .thenReturn(Optional.of(1));
        when(loginAttemptRepository.findFailCountWithinWindow(eq(IP_KEY), any(Instant.class)))
                .thenReturn(Optional.of(30));

        assertThatThrownBy(() -> limiter.checkBlocked(EMAIL))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.TOO_MANY_ATTEMPTS.name()));
    }

    @Test
    void recordFailure는_email과_ip_카운터를_모두_증가시킨다() {
        limiter.recordFailure(EMAIL);

        verify(loginAttemptRepository).increment(eq(EMAIL_KEY), any(Instant.class), any(Instant.class));
        verify(loginAttemptRepository).increment(eq(IP_KEY), any(Instant.class), any(Instant.class));
    }
}
