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

    // X-Forwarded-For의 첫 항목은 클라이언트가 그대로 채워 보낼 수 있어 신뢰할 수 없다 -- 요청마다 임의
    // 값을 넣으면 IP 리밋이 무력화되고, 반대로 남의 IP를 넣으면 그 IP를 대신 차단시킬 수도 있다.
    // 리버스 프록시(nginx)가 클라이언트 값을 무시하고 자기가 관측한 주소로 덮어쓰는 X-Real-IP만 신뢰하고,
    // 프록시가 없는 로컬 실행에서는 소켓 주소로 폴백한다(deploy/nginx/api.at-crew.com.conf와 한 쌍).
    private String extractIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            HttpServletRequest request = attrs.getRequest();
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
