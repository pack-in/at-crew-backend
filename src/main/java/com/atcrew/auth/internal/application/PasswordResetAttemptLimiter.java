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

    // 코드 오답 시도 제한(이슈 #151) — 6자리 코드(32^6 조합)는 시도 횟수를 제한하지 않으면 TTL(10분) 안에
    // 무차별 대입이 시도될 수 있다. 윈도우를 코드 TTL과 동일하게(600초) 잡아 코드 하나의 생애주기 전체를
    // 덮는다 — LoginAttemptLimiter.WINDOW_SECONDS와 같은 값이라 AuthCleanupScheduler가 추가 정리 없이 커버한다.
    private static final int VERIFY_ATTEMPT_LIMIT = 5;
    private static final int VERIFY_WINDOW_SECONDS = 600;

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

    @Transactional(readOnly = true)
    void checkVerifyBlocked(String email) {
        Instant windowStart = Instant.now().minusSeconds(VERIFY_WINDOW_SECONDS);
        Integer count = loginAttemptRepository
                .findFailCountWithinWindow("pwverify:" + email, windowStart).orElse(null);
        if (count != null && count >= VERIFY_ATTEMPT_LIMIT) {
            log.warn("비밀번호 재설정 코드 검증 차단: email={}", LogMask.email(email));
            throw new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordVerifyFailure(String email) {
        Instant now = Instant.now();
        loginAttemptRepository.increment("pwverify:" + email, now, now.minusSeconds(VERIFY_WINDOW_SECONDS));
    }
}
