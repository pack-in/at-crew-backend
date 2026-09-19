package com.atcrew.auth.internal.infra.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnExpression("'${google.oauth.client-ids:}'.trim().length() > 0")
class GoogleOAuthConfig {

    // 허용 audience 목록 — 지금은 웹 Client ID 하나지만, 모바일 앱 Client ID가 늘어도 설정값만 추가하면 된다.
    @Value("${google.oauth.client-ids}")
    private String clientIds;

    @Bean
    GoogleTokenVerifierPort googleTokenVerifierPort() {
        Set<String> audiences = Arrays.stream(clientIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audiences)
                // Google이 발급한 ID Token의 iss는 이 둘 중 하나다(https 접두사 유무는 발급 시점에 따라 다르다).
                .setIssuers(List.of("accounts.google.com", "https://accounts.google.com"))
                .build();
        return new GoogleTokenVerifierPortImpl(verifier);
    }
}
