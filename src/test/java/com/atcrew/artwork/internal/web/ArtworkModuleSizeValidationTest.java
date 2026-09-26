package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.BookmarkService;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * artwork 모듈 커서 목록 API의 {@code size} 파라미터 검증(이슈 #195 후속).
 *
 * <p>{@code CommunitySizeValidationTest}와 같은 이유 — 음수 size를 그대로 흘려보내면
 * {@code limit = size + 1}이 0 이하가 돼 {@code PageRequest.of}가 IllegalArgumentException을
 * 던지고 GlobalExceptionHandler의 범용 핸들러가 500으로 돌려준다. 음수만 400으로 막고
 * size=0은 빈 목록을 돌려주는 기존 동작(이슈 #195 본 수정)을 유지한다.
 */
class ArtworkModuleSizeValidationTest {

    SecurityUtils securityUtils;

    @BeforeEach
    void setUp() {
        securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentMemberId()).thenReturn("member-1");
    }

    @Test
    void 내_작품_목록_음수_size는_400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ArtworkController(mock(ArtworkService.class), securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/members/me/artworks").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @Test
    void 내_작품_목록_size가_0이면_통과한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ArtworkController(mock(ArtworkService.class), securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/members/me/artworks").param("size", "0"))
                .andExpect(status().isOk());
    }

    @Test
    void 북마크_목록_음수_size는_400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new BookmarkController(mock(BookmarkService.class), securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/bookmarks").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @Test
    void 휴지통_목록_음수_size는_400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TrashController(mock(ArtworkService.class), securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/trash/artworks").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }
}
