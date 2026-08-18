package com.atcrew.auth.internal.application;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.auth.internal.persistence.LoginAttemptRepository;
import com.atcrew.common.logging.LogMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// login_attempts 테이블을 그대로 재사용한다 — attempt_key가 "email:"/"ip:" 접두사로 용도를 구분하는
// 범용 설계라(LoginAttemptLimiter 참고) 새 테이블 없이 "pwreset:" 접두사만 추가하면 된다.
// AuthCleanupScheduler의 만료 정리는 LoginAttemptLimiter.WINDOW_SECONDS(600초, 이 클래스의 300초보다 김)를
// 기준으로 돌기 때문에 이 클래스의 행도 함께 안전하게 정리된다 — 별도 정리 쿼리 불필요.
@Service
class PasswordResetAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetAttemptLimiter.class);
    private static final int EMAIL_LIMIT = 3;
    private static final int WINDOW_SECONDS = 300; // 5분 (docs/design/auth-email-custom-redesign.md §7.3)

    private final LoginAttemptRepository loginAttemptRepository;

    PasswordResetAttemptLimiter(LoginAttemptRepository loginAttemptRepository) {
        this.loginAttemptRepository = loginAttemptRepository;
    }

    @Transactional(readOnly = true)
    void checkBlocked(String email) {
        Instant windowStart = Instant.now().minusSeconds(WINDOW_SECONDS);
        Integer count = loginAttemptRepository
                .findFailCountWithinWindow("pwreset:" + email, windowStart).orElse(null);
        if (count != null && count >= EMAIL_LIMIT) {
            log.warn("비밀번호 재설정 요청 차단: email={}", LogMask.email(email));
            throw new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS);
        }
    }

    // 로그인 실패 카운터와 동일하게, 상위 트랜잭션이 롤백돼도 요청 횟수는 남아야 하므로 분리한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordAttempt(String email) {
        Instant now = Instant.now();
        loginAttemptRepository.increment("pwreset:" + email, now, now.minusSeconds(WINDOW_SECONDS));
    }
}
