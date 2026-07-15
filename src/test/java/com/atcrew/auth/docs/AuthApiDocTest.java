package com.atcrew.auth.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 API 문서화 통합 테스트.
 *
 * <p>실제 MongoDB Testcontainer와 전체 Spring 컨텍스트를 기동해
 * 이메일 회원가입·로그인·토큰 갱신 API의 요청/응답 구조를 REST Docs 스니펫으로 생성한다.
 */
class AuthApiDocTest extends RestDocsIntegrationSupport {

    /**
     * 이메일 회원가입 성공 시나리오 문서화.
     * 요청 필드와 응답 필드를 모두 스니펫으로 생성한다.
     */
    @Test
    void 이메일_회원가입_성공_문서화() throws Exception {
        String uniqueEmail = "doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail,
                                "Secure1!",
                                "Secure1!",
                                "문서화유저",
                                true, true, true, false,
                                "Asia/Seoul"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andDo(document("auth/email-register",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("이메일 주소"),
                                fieldWithPath("password").description("비밀번호 (영문·숫자·특수문자 포함 8자 이상)"),
                                fieldWithPath("passwordConfirm").description("비밀번호 확인"),
                                fieldWithPath("name").description("이름·작가명 (최대 16자)"),
                                fieldWithPath("agreeService").description("서비스 이용약관 동의 (필수)"),
                                fieldWithPath("agreePrivacy").description("개인정보처리방침 동의 (필수)"),
                                fieldWithPath("agreeThirdParty").description("제3자 정보제공 동의 (선택)"),
                                fieldWithPath("agreeMarketing").description("마케팅 정보 수신 동의 (선택)"),
                                fieldWithPath("timezone").description("IANA 시간대 ID, 클라이언트 자동감지값 (예: Asia/Seoul)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.accessToken").description("액세스 토큰 (JWT)"),
                                fieldWithPath("data.refreshToken").description("리프레시 토큰 (JWT)"),
                                fieldWithPath("data.isNewUser").description("신규 가입 여부"),
                                fieldWithPath("data.member.id").description("회원 고유 식별자"),
                                fieldWithPath("data.member.handle").description("회원 핸들 (@아이디)"),
                                fieldWithPath("data.member.authProvider").description("인증 제공자 (EMAIL)"),
                                fieldWithPath("data.member.name").description("이름·작가명"),
                                fieldWithPath("data.member.employmentStatus").description("구직 상태"),
                                fieldWithPath("data.member.totalSlotCount").description("총 슬롯 수"),
                                fieldWithPath("data.member.availableSlotCount").description("가용 슬롯 수"),
                                fieldWithPath("data.member.active").description("계정 활성화 여부"),
                                fieldWithPath("data.member.createdAt").description("가입 일시 (ISO 8601)"),
                                fieldWithPath("data.member.updatedAt").description("최종 수정 일시 (ISO 8601)"),
                                fieldWithPath("data.member.timezone").description("회원 시간대 (IANA tz ID)")
                        )
                ));
    }

    /**
     * 이메일 로그인 성공 시나리오 문서화.
     * 먼저 회원가입 API로 계정을 생성한 후 로그인 API를 호출해 스니펫을 생성한다.
     */
    @Test
    void 이메일_로그인_성공_문서화() throws Exception {
        // 테스트용 계정 먼저 생성
        String uniqueEmail = "doc-login-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "로그인문서유저",
                                true, true, true, false, "Asia/Seoul"
                        ))))
                .andExpect(status().isCreated());

        // 로그인 API 호출 및 문서화
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail, "Secure1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("auth/email-login",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("이메일 주소"),
                                fieldWithPath("password").description("비밀번호")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.accessToken").description("액세스 토큰 (JWT)"),
                                fieldWithPath("data.refreshToken").description("리프레시 토큰 (JWT)"),
                                fieldWithPath("data.isNewUser").description("신규 가입 여부 (로그인 시 false)"),
                                fieldWithPath("data.member.id").description("회원 고유 식별자"),
                                fieldWithPath("data.member.handle").description("회원 핸들 (@아이디)")
                        )
                ));
    }

    /**
     * 토큰 갱신 성공 시나리오 문서화.
     * 회원가입 API 응답에서 리프레시 토큰을 추출해 갱신 API를 호출한다.
     */
    @Test
    void 토큰_갱신_성공_문서화() throws Exception {
        // 회원가입으로 refreshToken 획득
        String uniqueEmail = "doc-refresh-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "갱신문서유저",
                                true, true, true, false, "Asia/Seoul"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        // 응답에서 refreshToken 추출
        String refreshToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .at("/data/refreshToken").asText();

        // 토큰 갱신 API 호출 및 문서화
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("auth/token-refresh",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("refreshToken").description("리프레시 토큰 (JWT)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.accessToken").description("새로 발급된 액세스 토큰 (JWT)"),
                                fieldWithPath("data.refreshToken").description("새로 발급된 리프레시 토큰 (JWT)")
                        )
                ));
    }

    // ─── 요청 바디 내부 레코드 ───────────────────────────────────────────

    /** 이메일 회원가입 요청 바디 */
    record RegisterRequest(
            String email,
            String password,
            String passwordConfirm,
            String name,
            boolean agreeService,
            boolean agreePrivacy,
            boolean agreeThirdParty,
            boolean agreeMarketing,
            String timezone
    ) {}

    /** 이메일 로그인 요청 바디 */
    record LoginRequest(String email, String password) {}

    /** 토큰 갱신 요청 바디 */
    record RefreshRequest(String refreshToken) {}
}
