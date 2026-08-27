package com.atcrew.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

    // 관리 엔드포인트(actuator) 전용 체인 — 관측 설계(docs/design/observability-design.md §3.1).
    // actuator는 management.server.port(8081)로 분리돼 서비스 포트(8080)에는 아예 존재하지 않고,
    // 컨테이너 포트도 호스트 루프백에만 바인딩한다(deploy/docker-compose.app.yml). 즉 접근 통제는
    // 네트워크 경계가 담당하고, 여기서는 같은 JVM 안의 Alloy 스크레이프가 401로 막히지 않게만 한다.
    // 외부 공개용 /healthz는 nginx가 이 포트의 liveness 그룹으로 프록시한다.
    @Bean
    @Order(0)
    SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
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

        // origin 레벨 HTTPS 강제는 안 쓴다 — Cloudflare Flexible 모드가 origin에는 평문 HTTP로 전달하는
        // 구조라 requiresChannel()을 켜면 Cloudflare발 트래픽이 전부 거부된다. 클라이언트↔Cloudflare 구간의
        // HTTPS는 Cloudflare가 이미 강제하므로 origin에서 이중으로 강제할 필요가 없다(위 HSTS 헤더는 별개로
        // 유지 — 그건 브라우저 대상이라 이 구조와 무관하게 계속 유효함). 참고로 requiresChannel()이 참조하는
        // ChannelDecisionManager가 Spring Security 7.0.5에서 런타임에 없어 NoClassDefFoundError로 죽는
        // 문제도 있었다(prod 프로필로 실제 기동해본 건 이번이 처음이라 지금까지 발견되지 않았음).

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
                            .requestMatchers(HttpMethod.POST, "/internal/media/images/processed").permitAll()
                            // 구 경로 — Worker 전환 기간 한정 shim(docs/design/media-module-design.md §9.2).
                            // Worker가 새 형식으로 전환된 뒤 LegacyArtworkCallbackController와 함께 제거한다.
                            .requestMatchers(HttpMethod.POST, "/internal/artwork/images/processed").permitAll()
                            .requestMatchers(HttpMethod.POST, "/internal/search/reindex").permitAll()
                            // 요금제 페이지는 비로그인도 열람한다(요금제-R03).
                            .requestMatchers(HttpMethod.GET, "/api/billing/catalog").permitAll()
                            // Stripe 웹훅 — 인증 대신 서명 검증으로 보호한다(BillingWebhookController).
                            .requestMatchers(HttpMethod.POST, "/internal/billing/stripe/webhook").permitAll();

                    // recruit 모듈 — 공개 목록/상세 조회는 인증 불필요, 나머지는 인증 필요(기본 anyRequest().authenticated()).
                    // 주의: /job-postings/{jobPostingId} 템플릿보다 /trash, /me 같은 리터럴 하위 경로를 먼저 선언해야
                    // 경로 매처가 리터럴 경로를 템플릿에 잘못 매칭시켜 인증을 우회하는 것을 방지할 수 있다.
                    auth.requestMatchers(HttpMethod.GET, "/api/recruit/job-postings/trash").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/job-postings/me").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/job-postings").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/job-postings/{jobPostingId}").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/team-postings/trash").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/team-postings/me").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/team-postings").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/team-postings/{teamPostingId}").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/job-seeking-posts/trash").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/job-seeking-posts/me").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/job-seeking-posts").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/recruit/job-seeking-posts/{jobSeekingPostId}").permitAll();
                    // portfolio 모듈 — 공유 링크 열람만 인증 불필요, 나머지는 기본값 anyRequest().authenticated()가 커버한다.
                    // /{portfolioId} 템플릿은 permitAll로 선언하지 않으므로 리터럴 경로(/me, /selectable, /shared)가
                    // 템플릿에 가려져 인증이 우회될 여지가 없다 — 추후 /{portfolioId}를 공개로 열 때는
                    // recruit와 같은 함정에 주의해 리터럴 경로를 먼저 선언해야 한다.
                    auth.requestMatchers(HttpMethod.GET, "/api/portfolios/shared/{identifier}").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/portfolios/shared/{identifier}/artworks").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/portfolios/shared/{identifier}/snapshots/{snapshotId}")
                            .permitAll();

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
