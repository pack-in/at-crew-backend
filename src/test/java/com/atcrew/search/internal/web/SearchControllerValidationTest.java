package com.atcrew.search.internal.web;

import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.search.SearchPage;
import com.atcrew.search.SearchQuery;
import com.atcrew.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SearchController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 size 범위·필터 파라미터 바인딩을 검증한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class SearchControllerValidationTest {

    MockMvc mockMvc;
    SearchService searchService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        searchService = mock(SearchService.class);
        when(searchService.search(any())).thenReturn(SearchPage.empty());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SearchController(searchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    @Test
    void size_1_미만이면_400() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "고양이").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"))
                .andDo(document("search/validation/size-below-minimum"));
    }

    @Test
    void size가_최대치를_넘으면_50으로_잘린다() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "고양이").param("size", "999"))
                .andExpect(status().isOk());

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchService).search(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(50);
    }

    @Test
    void 잘못된_enum_값이면_400() throws Exception {
        mockMvc.perform(get("/api/search").param("artworkFields", "존재하지않는값"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 검색어와_필터가_모두_비어있어도_200이고_서비스에_그대로_위임한다() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("search/search-empty-criteria"));

        // 최초 진입 상태 판단은 서비스 계층(SearchServiceImpl.isEmptyCriteria)의 책임 — 컨트롤러는 그대로 위임한다
        verify(searchService).search(any());
    }
}
