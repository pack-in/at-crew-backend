package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.BookmarkService;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BookmarkController 북마크 폴더 이름 변경(PATCH /api/bookmarks/folders/{folderId}) 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다
 * ({@code CompanyControllerValidationTest}와 동일한 패턴).
 */
@ExtendWith(RestDocumentationExtension.class)
class BookmarkControllerValidationTest {

    private static final String FOLDER_ID = UUID.randomUUID().toString();

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    BookmarkService bookmarkService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        bookmarkService = mock(BookmarkService.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BookmarkController(bookmarkService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    @Test
    void 폴더_이름_변경_빈_문자열_거부() throws Exception {
        mockMvc.perform(patch("/api/bookmarks/folders/{folderId}", FOLDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("bookmark/validation/rename-folder-blank-name"));
    }

    @Test
    void 폴더_이름_변경_21자_이상_거부() throws Exception {
        mockMvc.perform(patch("/api/bookmarks/folders/{folderId}", FOLDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "가".repeat(21)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("bookmark/validation/rename-folder-name-too-long"));
    }

    @Test
    void 폴더_이름_변경_name_누락_거부() throws Exception {
        mockMvc.perform(patch("/api/bookmarks/folders/{folderId}", FOLDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("bookmark/validation/rename-folder-missing-name"));
    }
}
