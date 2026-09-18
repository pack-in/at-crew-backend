package com.atcrew.auth.internal.infra.google;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class GoogleOAuthFallbackConfig {

    // Client ID 미설정 환경에서도 애플리케이션은 기동되고 이메일 로그인은 정상 동작한다 —
    // Google 로그인 경로만 503으로 막는다.
    @Bean
    @ConditionalOnMissingBean(GoogleTokenVerifierPort.class)
    GoogleTokenVerifierPort noOpGoogleTokenVerifierPort() {
        return idToken -> {
            throw new AuthException(AuthErrorCode.GOOGLE_LOGIN_NOT_CONFIGURED);
        };
    }
}
