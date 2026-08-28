package com.atcrew.community.internal.web;

import com.atcrew.artwork.ArtworkService;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.member.MemberService;
import com.atcrew.recruit.RecruitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티 목록 API의 {@code size} 파라미터 검증.
 *
 * <p>음수를 그대로 흘려보내면 조회 계층에서 500이 났다(2026-08-28 prod에서 확인 —
 * 인증이 필요 없는 공개 엔드포인트라 누구나 재현할 수 있었다). 0은 빈 목록을 돌려주는
 * 기존 동작이므로 그대로 두고, 음수만 400으로 막는다.
 */
class CommunitySizeValidationTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommunityController(
                        mock(ArtworkService.class), mock(MemberService.class), mock(RecruitService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest(name = "{0} — size=-1이면 400")
    @ValueSource(strings = {"/api/community/artworks", "/api/community/authors",
            "/api/community/job-postings", "/api/community/team-recruits"})
    @DisplayName("음수 size는 400 INVALID_SIZE로 거부한다")
    void negativeSizeIsRejected(String path) throws Exception {
        mockMvc.perform(get(path).param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @ParameterizedTest(name = "{0} — size=0은 통과")
    @ValueSource(strings = {"/api/community/artworks", "/api/community/authors",
            "/api/community/job-postings", "/api/community/team-recruits"})
    @DisplayName("size=0은 빈 목록을 돌려주는 기존 동작을 유지한다")
    void zeroSizeIsAccepted(String path) throws Exception {
        mockMvc.perform(get(path).param("size", "0"))
                .andExpect(status().isOk());
    }
}
