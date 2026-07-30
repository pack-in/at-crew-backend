package com.atcrew.auth.internal.application;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.auth.internal.persistence.LoginAttemptRepository;
import com.atcrew.common.logging.LogMask;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

@Service
class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);
    private static final int EMAIL_LIMIT = 5;
    private static final int IP_LIMIT = 30;
    static final int WINDOW_SECONDS = 600; // 10분 — AuthCleanupScheduler가 같은 윈도우 기준으로 만료 행을 지운다

    private final LoginAttemptRepository loginAttemptRepository;

    LoginAttemptLimiter(LoginAttemptRepository loginAttemptRepository) {
        this.loginAttemptRepository = loginAttemptRepository;
    }

    // 현재 차단 상태인지 확인 — 차단 중이면 BCrypt 연산 전에 429 반환
    @Transactional(readOnly = true)
    void checkBlocked(String email) {
        String ip = extractIp();
        Instant windowStart = windowStart();
        Integer emailFails = loginAttemptRepository
                .findFailCountWithinWindow("email:" + email, windowStart).orElse(null);
        Integer ipFails = loginAttemptRepository
                .findFailCountWithinWindow("ip:" + ip, windowStart).orElse(null);

        if ((emailFails != null && emailFails >= EMAIL_LIMIT) || (ipFails != null && ipFails >= IP_LIMIT)) {
            log.warn("로그인 차단: email={} ip={}", LogMask.email(email), ip);
            throw new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS);
        }
    }

    // 실패 누적은 로그인 트랜잭션과 분리한다 — 로그인 실패는 예외를 던져 호출자 트랜잭션이 롤백되므로
    // 같은 트랜잭션에서 증가시키면 카운터가 함께 사라져 레이트리밋이 무력화된다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordFailure(String email) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);
        loginAttemptRepository.increment("email:" + email, now, windowStart);
        loginAttemptRepository.increment("ip:" + extractIp(), now, windowStart);
    }

    @Transactional
    void reset(String email) {
        loginAttemptRepository.deleteByAttemptKey("email:" + email);
    }

    private Instant windowStart() {
        return Instant.now().minusSeconds(WINDOW_SECONDS);
    }

    private String extractIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            HttpServletRequest request = attrs.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
