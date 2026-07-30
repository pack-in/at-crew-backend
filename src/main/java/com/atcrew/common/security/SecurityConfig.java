package com.atcrew.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    private final Environment environment;

    SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // strength 10 (기본값)
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
            JwtAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless JWT — CSRF 불필요 (쿠키 전환 시 재검토)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // HSTS — HTTPS 응답에만 포함되므로 항상 활성화
        http.headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000)));

        // prod: HTTP → HTTPS 강제 리다이렉트 (LB 뒤라면 server.forward-headers-strategy=framework 필수)
        if (isProd()) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        return http
                .authorizeHttpRequests(auth -> {
                    // 인증 없이 접근 가능한 엔드포인트 — 와일드카드 대신 개별 명시 (향후 auth 하위 인증 필요 API 사고 방지)
                    auth.requestMatchers(HttpMethod.POST,
                                    "/api/auth/email/login",
                                    "/api/auth/email/register",
                                    "/api/auth/email/password-reset/request",
                                    "/api/auth/email/password-reset/confirm",
                                    "/api/auth/google/login",
                                    "/api/auth/google/register",
                                    "/api/auth/refresh").permitAll();

                    // prod 프로파일에서는 차단 (DevMemberController도 prod에서 미로드 — 이중 방어)
                    if (!isProd()) {
                        auth.requestMatchers(HttpMethod.POST, "/api/members").permitAll();
                    }
                    auth.requestMatchers(HttpMethod.GET, "/api/members/{handle}").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/artworks/{artworkId}").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/community/artworks").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/community/authors").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/community/job-postings").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/community/team-recruits").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/community/banners").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/search").permitAll()
                            // 기업 마이페이지는 비로그인도 열람 가능 — 단, /me는 본인 전용이므로
                            // {companyId} 공개 패턴에 가려지지 않도록 먼저 선언한다.
                            .requestMatchers(HttpMethod.GET, "/api/companies/me").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/companies/{companyId}").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/companies/{companyId}/careers").permitAll()
                            .requestMatchers(HttpMethod.POST, "/internal/artwork/images/processed").permitAll()
                            .requestMatchers(HttpMethod.POST, "/internal/search/reindex").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/info").permitAll();
                    if (!isProd()) {
                        auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
