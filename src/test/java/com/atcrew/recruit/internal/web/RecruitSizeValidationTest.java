package com.atcrew.recruit.internal.web;

import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.recruit.RecruitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * recruit 모듈 커서 목록 API의 {@code size} 파라미터 검증(이슈 #195 후속).
 *
 * <p>{@code CommunitySizeValidationTest}와 같은 이유 — 음수 size를 그대로 흘려보내면
 * {@code limit = size + 1}이 0 이하가 돼 {@code PageRequest.of}가 IllegalArgumentException을
 * 던지고 GlobalExceptionHandler의 범용 핸들러가 500으로 돌려준다.
 */
@Disabled("MVP 범위 밖 — recruit(구인·구직·팀원모집) 미출시, 출시 시 해제")
class RecruitSizeValidationTest {

    RecruitService recruitService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp() {
        recruitService = mock(RecruitService.class);
        securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentMemberId()).thenReturn("member-1");
    }

    @Test
    void 구인글_목록_음수_size는_400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new JobPostingController(recruitService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/recruit/job-postings").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @Test
    void 구직글_목록_음수_size는_400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new JobSeekingPostController(recruitService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/recruit/job-seeking-posts").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @Test
    void 팀원모집글_목록_음수_size는_400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TeamPostingController(recruitService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/recruit/team-postings").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @Test
    void 관심_작가_목록_음수_size는_400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LikedArtistController(recruitService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/recruit/liked-artists").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @Test
    void 구인글_지원자_목록_음수_size는_400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ApplicationController(recruitService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/recruit/job-postings/{jobPostingId}/applications", UUID.randomUUID())
                        .param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }
}
