package com.atcrew;

import com.atcrew.common.security.JwtProvider;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestMongoConfig.class)
class SecurityIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @LocalServerPort
    int port;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    MemberService memberService;

    RestClient rest;

    @BeforeEach
    void setUp() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // 4xx/5xx를 예외로 던지지 않고 ResponseEntity로 반환
                .defaultStatusHandler(status -> true, (req, resp) -> {})
                .build();
    }

    // ─── 섹션 1: 보호된 엔드포인트 토큰 없음 → 401 ──────────────────────

    @Test
    void 이름수정_토큰_없음_401() {
        ResponseEntity<String> res = exchange("/api/members/me/name", HttpMethod.PATCH,
                null, "{\"name\":\"홍길동\"}");
        assertUnauthorized(res);
    }

    @Test
    void 프로필수정_토큰_없음_401() {
        ResponseEntity<String> res = exchange("/api/members/me/info", HttpMethod.PATCH,
                null, "{}");
        assertUnauthorized(res);
    }

    @Test
    void 경력추가_토큰_없음_401() {
        ResponseEntity<String> res = exchange("/api/members/me/careers", HttpMethod.POST,
                null, "{}");
        assertUnauthorized(res);
    }

    @Test
    void 경력삭제_토큰_없음_401() {
        ResponseEntity<String> res = exchange("/api/members/me/careers/some-id", HttpMethod.DELETE,
                null, null);
        assertUnauthorized(res);
    }

    @Test
    void 탈퇴_토큰_없음_401() {
        ResponseEntity<String> res = exchange("/api/members/me", HttpMethod.DELETE,
                null, null);
        assertUnauthorized(res);
    }

    // ─── 섹션 2: 유효하지 않은 JWT → 401 ─────────────────────────────

    @Test
    void 형식_위반_JWT_401() {
        ResponseEntity<String> res = exchange("/api/members/me/name", HttpMethod.PATCH,
                "garbage.jwt.value", "{\"name\":\"홍길동\"}");
        assertUnauthorized(res);
    }

    // ─── 섹션 3: Refresh 토큰을 Access 토큰으로 오용 → 401 ──────────

    @Test
    void refresh_토큰_Authorization_헤더_사용_401() {
        String refresh = jwtProvider.generateRefreshToken("test-member-id");
        ResponseEntity<String> res = exchange("/api/members/me/name", HttpMethod.PATCH,
                refresh, "{\"name\":\"홍길동\"}");
        assertUnauthorized(res);
    }

    // ─── 섹션 4: 유효한 Access 토큰 → 인증 통과 ─────────────────────

    @Test
    void 유효한_토큰_이름수정_204() {
        MemberInfo member = memberService.register(uniqueEmail(), uniqueHandle(), "테스트유저", CreatorRole.WEBTOON);
        String token = jwtProvider.generateAccessToken(member.id(), member.loginEmail());

        ResponseEntity<String> res = exchange("/api/members/me/name", HttpMethod.PATCH,
                token, "{\"name\":\"새이름\"}");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void 유효한_토큰_탈퇴_204() {
        MemberInfo member = memberService.register(uniqueEmail(), uniqueHandle(), "탈퇴유저", CreatorRole.OTHER);
        String token = jwtProvider.generateAccessToken(member.id(), member.loginEmail());

        ResponseEntity<String> res = exchange("/api/members/me", HttpMethod.DELETE,
                token, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ─── 섹션 5: 공개 엔드포인트 → 토큰 없이 접근 가능 ─────────────

    @Test
    void 핸들_조회_토큰_없이_401_아님() {
        // 없는 핸들이어도 보안 필터를 통과 → 404
        ResponseEntity<String> res = exchange("/api/members/nonexistent_handle",
                HttpMethod.GET, null, null);
        assertThat(res.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 이메일_로그인_엔드포인트_토큰_없이_401_아님() {
        ResponseEntity<String> res = exchange("/api/auth/email/login", HttpMethod.POST,
                null, "{}");
        assertThat(res.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void Google_로그인_엔드포인트_토큰_없이_401_아님() {
        ResponseEntity<String> res = exchange("/api/auth/google/login", HttpMethod.POST,
                null, "{}");
        assertThat(res.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 헬스체크_토큰_없이_200() {
        ResponseEntity<String> res = exchange("/actuator/health", HttpMethod.GET,
                null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    private void assertUnauthorized(ResponseEntity<String> res) {
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).contains("UNAUTHENTICATED");
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String bearerToken, String body) {
        RestClient.RequestBodySpec req = rest.method(method).uri(path)
                .contentType(MediaType.APPLICATION_JSON);

        if (bearerToken != null) {
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }

        if (body != null) {
            return req.body(body).retrieve().toEntity(String.class);
        }
        return req.retrieve().toEntity(String.class);
    }

    private String uniqueEmail() {
        return "sec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@test.com";
    }

    private String uniqueHandle() {
        return "sec" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
