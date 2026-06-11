package com.atcrew.auth.internal.infra.firebase;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FirebaseFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(FirebaseVerifier.class)
    FirebaseVerifier noOpFirebaseVerifier() {
        return idToken -> {
            throw new AuthException(AuthErrorCode.FIREBASE_NOT_CONFIGURED);
        };
    }
}
