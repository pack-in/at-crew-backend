package com.atcrew.community.internal.web;

import com.atcrew.artwork.ArtworkService;
import com.atcrew.common.response.OffsetPage;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.member.MemberService;
import com.atcrew.recruit.RecruitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티 목록 API의 페이지 계약.
 *
 * <p>화면이 번호 페이지네이션이라 오프셋 방식이다(docs/design/community-module-design.md §6).
 * 페이지 번호는 1부터 세고, 전체 개수는 번호를 그리는 데 필요하므로 모든 페이지 응답에 담는다.
 * OFFSET은 건너뛸 행을 실제로 읽으므로 상한을 넘는 요청은 거부한다.
 */
class CommunityTotalCountTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ArtworkService artworkService = mock(ArtworkService.class);
        MemberService memberService = mock(MemberService.class);
        RecruitService recruitService = mock(RecruitService.class);
        // 전체 45건 중 한 페이지분 — 목록과 개수를 한 번에 돌려준다
        when(artworkService.getCommunityArtworks(any(), any(), any(), any(), anyInt(), anyInt(), any(), anyBoolean()))
                .thenReturn(new OffsetPage<>(List.of(), 45));
        when(memberService.searchProfiles(any())).thenReturn(new OffsetPage<>(List.of(), 45));
        when(recruitService.getJobPostingFeed(anyInt(), anyInt())).thenReturn(new OffsetPage<>(List.of(), 45));
        when(recruitService.getTeamRecruitFeed(anyInt(), anyInt())).thenReturn(new OffsetPage<>(List.of(), 45));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommunityController(artworkService, memberService, recruitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest(name = "{0} — 페이지 정보와 전체 개수를 함께 내려준다")
    @ValueSource(strings = {"/api/community/artworks", "/api/community/authors",
            "/api/community/job-postings", "/api/community/team-recruits"})
    @DisplayName("모든 페이지 응답에 totalCount와 페이지 정보가 있다")
    void everyPageCarriesTotalCount(String path) throws Exception {
        mockMvc.perform(get(path).param("page", "2").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(45))
                // 45건을 20개씩 나누면 3페이지이고, 2페이지 다음이 남는다
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @ParameterizedTest(name = "{0} — page 생략 시 1페이지")
    @ValueSource(strings = {"/api/community/artworks", "/api/community/authors",
            "/api/community/job-postings", "/api/community/team-recruits"})
    @DisplayName("page를 생략하면 1페이지로 본다")
    void pageDefaultsToFirst(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @ParameterizedTest(name = "{0} — page={1}이면 400")
    @CsvSource({
            "/api/community/artworks, 0",
            "/api/community/artworks, -1",
            // page × size가 조회 상한(10000)을 넘는다
            "/api/community/artworks, 501",
            "/api/community/authors, 501",
            "/api/community/job-postings, 501",
            "/api/community/team-recruits, 501"})
    @DisplayName("1 미만이거나 조회 상한을 넘는 page는 400 INVALID_PAGE로 거부한다")
    void invalidPageIsRejected(String path, int page) throws Exception {
        mockMvc.perform(get(path).param("page", String.valueOf(page)).param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE"));
    }

    @ParameterizedTest(name = "{0} — 상한 경계는 통과")
    @ValueSource(strings = {"/api/community/artworks", "/api/community/authors",
            "/api/community/job-postings", "/api/community/team-recruits"})
    @DisplayName("page × size가 상한과 같으면 통과한다")
    void offsetLimitBoundaryIsAccepted(String path) throws Exception {
        mockMvc.perform(get(path).param("page", "500").param("size", "20"))
                .andExpect(status().isOk());
    }
}
