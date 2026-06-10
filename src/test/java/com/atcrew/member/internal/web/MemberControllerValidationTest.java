package com.atcrew.member.internal.web;

import com.atcrew.common.GlobalExceptionHandler;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.member.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MemberControllerValidationTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    MemberService memberService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MemberController(memberService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── RegisterRequest ──────────────────────────────────────────────

    @Test
    void 이름_16자_초과_가입_거부() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "valid_handle", "가".repeat(17), "WEBTOON")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void 이메일_형식_아닌_값_가입_거부() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("not-an-email", "valid_handle", "홍길동", "WEBTOON")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void 핸들_패턴_위반_가입_거부() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "invalid handle!", "홍길동", "WEBTOON")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void 존재하지_않는_enum_값_가입_거부() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "valid_handle", "홍길동", "INVALID_ROLE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void 이름_빈_문자열_가입_거부() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "valid_handle", "   ", "WEBTOON")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    // ─── UpdateNameRequest ────────────────────────────────────────────

    @Test
    void 이름_수정_빈_문자열_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void 이름_수정_16자_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "가".repeat(17)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
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
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
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
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
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
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    // ─── UpdateInfoRequest ────────────────────────────────────────────

    @Test
    void 슬롯_개수_최대_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("totalSlotCount", 6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void 슬롯_개수_최솟값_미만_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("totalSlotCount", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void 연락처_100자_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("contact", "a".repeat(101)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void 활동_분야_4개_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityFields", List.of("ILLUSTRATION", "WEBTOON", "MANGA", "ANIMATION", "ILLUSTRATION")
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    // ─── Helper ───────────────────────────────────────────────────────

    private String registerBody(String email, String handle, String name, String role) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "loginEmail", email,
                "handle", handle,
                "name", name,
                "creatorRole", role
        ));
    }
}
