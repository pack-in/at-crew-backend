package com.atcrew.search.docs;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.ImageProcessedCallbackCommand;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.Visibility;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberService;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색 API 문서화 통합 테스트.
 *
 * <p>실제 MongoDB·Elasticsearch Testcontainer와 전체 Spring 컨텍스트를 기동해
 * 필터 조합 검색과 최초 진입(결과 미노출) 상태의 요청/응답 구조를 REST Docs 스니펫으로 생성한다.
 */
class SearchApiDocTest extends RestDocsIntegrationSupport {

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Test
    void 필터_검색_성공_문서화() throws Exception {
        uploadReadyArtwork();

        // ArtworkSearchIndexer는 비동기라 색인 반영까지 폴링한다
        awaitIndexed();

        mockMvc.perform(get("/api/search")
                        .param("artworkFields", "ILLUSTRATION")
                        .param("ageRatings", "ALL")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("search/search-success",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("q").description("검색어").optional(),
                                parameterWithName("artworkFields").description("작품 분야 필터 (다중선택)").optional(),
                                parameterWithName("ageRatings").description("연령대 필터 (다중선택)").optional(),
                                parameterWithName("size").description("페이지 크기 (기본 20, 최대 50)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("결과 ID"),
                                fieldWithPath("data.items[].postType").description("게시글 유형 (PORTFOLIO 등)"),
                                fieldWithPath("data.items[].title").description("제목"),
                                fieldWithPath("data.totalCount").description("전체 결과 건수"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 검색어와_필터가_모두_없으면_빈_결과_문서화() throws Exception {
        // 피그마 "검색 페이지 최초 진입" 규칙 — 검색어/필터 적용 전에는 결과 목록을 노출하지 않는다
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andDo(document("search/search-empty-criteria",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items").description("검색 결과 목록 (최초 진입 상태라 항상 빈 배열)"),
                                fieldWithPath("data.totalCount").description("전체 결과 건수 (0)")
                        )
                ));
    }

    @Test
    void 구인글만_요청하면_recruit_모듈_미구현으로_빈_목록_문서화() throws Exception {
        mockMvc.perform(get("/api/search").param("postTypes", "JOB_POSTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andDo(document("search/search-recruit-not-implemented"));
    }

    private void uploadReadyArtwork() throws InterruptedException {
        String memberId = memberService.register(
                "search-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "searchdoc" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                "검색문서작가", CreatorRole.WEBTOON).id();

        List<String> imageKeys = List.of("raw/" + UUID.randomUUID() + ".png");
        ArtworkInfo artwork = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                imageKeys, 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "검색문서화 작품", "설명",
                ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL, List.of(ArtworkRole.LINEART),
                List.of("BL"), List.of("태그"),
                AgeRating.ALL, Visibility.PUBLIC, List.of(), null, null, List.of(), List.of()
        ));

        artworkService.handleImageProcessedCallback(new ImageProcessedCallbackCommand(
                artwork.id(), imageKeys.get(0), "thumb-key", null, "orig.avif", ImageProcessingStatus.DONE));
    }

    private void awaitIndexed() throws InterruptedException {
        // ArtworkSearchIndexer가 비동기로 색인을 반영할 시간을 확보한다
        Thread.sleep(Duration.ofSeconds(3).toMillis());
    }
}
