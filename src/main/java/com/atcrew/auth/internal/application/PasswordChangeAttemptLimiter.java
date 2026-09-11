package com.atcrew.auth.internal.application;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.auth.internal.persistence.LoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// 비밀번호 변경(설정 화면) 1단계 — 현재 비밀번호 재확인 시도 제한 (이슈 #152, Figma "비밀번호를 여러 번
// 잘못 입력했어요" 문구). login_attempts 테이블을 "pwchange:" 접두사로 재사용한다
// (LoginAttemptLimiter/PasswordResetAttemptLimiter와 동일 패턴). 이미 액세스 토큰으로 인증된 호출이라
// email·IP가 아닌 memberId로 키를 잡는다 — 대입 시도는 탈취된 세션 안에서만 의미가 있기 때문이다.
@Service
class PasswordChangeAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeAttemptLimiter.class);
    private static final int ATTEMPT_LIMIT = 5;
    private static final int WINDOW_SECONDS = 600; // LoginAttemptLimiter.WINDOW_SECONDS와 동일 — 정리 배치가 같이 커버한다

    private final LoginAttemptRepository loginAttemptRepository;

    PasswordChangeAttemptLimiter(LoginAttemptRepository loginAttemptRepository) {
        this.loginAttemptRepository = loginAttemptRepository;
    }

    @Transactional(readOnly = true)
    void checkBlocked(String memberId) {
        Instant windowStart = Instant.now().minusSeconds(WINDOW_SECONDS);
        Integer count = loginAttemptRepository
                .findFailCountWithinWindow("pwchange:" + memberId, windowStart).orElse(null);
        if (count != null && count >= ATTEMPT_LIMIT) {
            log.warn("비밀번호 변경 재인증 차단: memberId={}", memberId);
            throw new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS);
        }
    }

    // 상위 트랜잭션이 롤백돼도 실패 횟수는 남아야 하므로 분리한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordFailure(String memberId) {
        Instant now = Instant.now();
        loginAttemptRepository.increment("pwchange:" + memberId, now, now.minusSeconds(WINDOW_SECONDS));
    }
}
