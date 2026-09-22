package com.atcrew.community;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티 목록 API의 페이지 파라미터를 실제 조회 계층까지 통과시켜 검증한다.
 *
 * <p>{@code CommunityTotalCountTest}·{@code CommunitySizeValidationTest}는 서비스를 목(mock)으로
 * 세우므로 조회 계층에서만 터지는 입력을 잡지 못한다. {@code size=0}이 그런 입력이었다 —
 * 컨트롤러는 0을 "빈 목록"으로 통과시키는데 {@code PageRequest}는 0을 거부해 500이 났다.
 */
class CommunityPagingIntegrationTest extends RestDocsIntegrationSupport {

    @ParameterizedTest(name = "{0} — size=0은 빈 목록")
    @ValueSource(strings = {"/api/community/artworks", "/api/community/authors",
            "/api/community/job-postings", "/api/community/team-recruits"})
    void size가_0이면_조회_없이_빈_목록을_돌려준다(String path) throws Exception {
        mockMvc.perform(get(path).param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @ParameterizedTest(name = "{0} — 마지막 페이지 뒤쪽도 빈 목록")
    @ValueSource(strings = {"/api/community/artworks", "/api/community/authors",
            "/api/community/job-postings", "/api/community/team-recruits"})
    void 데이터가_없는_페이지는_빈_목록을_돌려준다(String path) throws Exception {
        mockMvc.perform(get(path).param("page", "3").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
