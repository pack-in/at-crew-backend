package com.atcrew.member.internal.web;

import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.member.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MemberController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 * DevMemberController도 함께 등록하여 개발용 회원가입 엔드포인트 검증도 포함한다.
 * 각 케이스에 REST Docs 스니펫을 생성해 잘못된 요청의 응답 형태를 문서화한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class MemberControllerValidationTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    MemberService memberService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        memberService = mock(MemberService.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MemberController(memberService, securityUtils),
                        new DevMemberController(memberService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    // ─── RegisterRequest ──────────────────────────────────────────────

    @Test
    void 이름_16자_초과_가입_거부() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "valid_handle", "가".repeat(17))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/register-name-too-long"));
    }

    @Test
    void 이메일_형식_아닌_값_가입_거부() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("not-an-email", "valid_handle", "홍길동")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/register-invalid-email-format"));
    }

    @Test
    void 핸들_패턴_위반_가입_거부() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "invalid handle!", "홍길동")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/register-invalid-handle-pattern"));
    }

    @Test
    void 정보_수정_존재하지_않는_enum_값_거부() throws Exception {
        // 가입 요청(RegisterRequest)에는 enum 필드가 없어졌으므로(창작자 유형 제거) 정보 수정으로 검증한다.
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("activeRegion", "INVALID_REGION"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/update-info-invalid-enum-value"));
    }

    @Test
    void 이름_빈_문자열_가입_거부() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "valid_handle", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/register-blank-name"));
    }

    // ─── UpdateNameRequest ────────────────────────────────────────────

    @Test
    void 이름_수정_빈_문자열_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/update-name-blank"));
    }

    @Test
    void 이름_수정_16자_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "가".repeat(17)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/update-name-too-long"));
    }

    // ─── AddCareerRequest ─────────────────────────────────────────────

    @Test
    void 경력_제목_100자_초과_거부() throws Exception {
        mockMvc.perform(post("/api/members/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workTitle", "A".repeat(101),
                                "startDate", "2024.01.01",
                                "ongoing", false
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/add-career-title-too-long"));
    }

    @Test
    void 경력_시작일_미래날짜_거부() throws Exception {
        mockMvc.perform(post("/api/members/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workTitle", "작품명",
                                "startDate", "2099.01.01",
                                "ongoing", false
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/add-career-future-start-date"));
    }

    @Test
    void 경력_제목_없으면_거부() throws Exception {
        mockMvc.perform(post("/api/members/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workTitle", "",
                                "startDate", "2024.01.01",
                                "ongoing", false
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/add-career-blank-title"));
    }

    // ─── UpdateInfoRequest ────────────────────────────────────────────

    @Test
    void 슬롯_개수_최대_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("totalSlotCount", 6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/update-info-slot-count-too-high"));
    }

    @Test
    void 슬롯_개수_최솟값_미만_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("totalSlotCount", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/update-info-slot-count-too-low"));
    }

    @Test
    void 연락처_100자_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("contact", "a".repeat(101)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/update-info-contact-too-long"));
    }

    @Test
    void 활동_분야_4개_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityFields", List.of("ILLUSTRATION", "WEBTOON", "PRINT_COMIC", "ANIMATION", "ILLUSTRATION")
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/update-info-too-many-activity-fields"));
    }

    // ─── 설정 토글 ────────────────────────────────────────────────────

    @Test
    void 마케팅_동의_값_누락_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/marketing-agreement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/update-marketing-agreement-null"));
    }

    @Test
    void 성인_콘텐츠_표시_값_누락_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/adult-content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("member/validation/update-adult-content-null"));
    }

    // ─── Helper ───────────────────────────────────────────────────────

    private String registerBody(String email, String handle, String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "loginEmail", email,
                "handle", handle,
                "name", name
        ));
    }
}
