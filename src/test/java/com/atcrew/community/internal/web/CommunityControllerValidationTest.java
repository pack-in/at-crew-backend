package com.atcrew.community.internal.web;

import com.atcrew.artwork.ArtworkService;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.member.MemberService;
import com.atcrew.recruit.RecruitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CommunityController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 size 파라미터 검증을 확인한다.
 * 음수 size는 과거 검증 없이 그대로 PageRequest에 전달돼 500이 나던 결함이었다 —
 * resolveSize()에 하한 검사를 추가해 400 INVALID_SIZE로 고쳤다.
 */
@ExtendWith(RestDocumentationExtension.class)
class CommunityControllerValidationTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommunityController(
                        mock(ArtworkService.class), mock(MemberService.class), mock(RecruitService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    @Test
    void 작품_목록_size_음수_400() throws Exception {
        mockMvc.perform(get("/api/community/artworks").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"))
                .andDo(document("community/validation/artworks-negative-size"));
    }

    @Test
    void 작가_목록_size_음수_400() throws Exception {
        mockMvc.perform(get("/api/community/authors").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @Test
    void 구인글_목록_size_음수_400() throws Exception {
        mockMvc.perform(get("/api/community/job-postings").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @Test
    void 팀원모집글_목록_size_음수_400() throws Exception {
        mockMvc.perform(get("/api/community/team-recruits").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }
}
