package com.atcrew.search.docs;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.member.MemberService;
import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.JobEmploymentType;
import com.atcrew.recruit.JobPaymentType;
import com.atcrew.recruit.JobPaymentUnit;
import com.atcrew.recruit.JobPostingInfo;
import com.atcrew.recruit.JobWorkLocationType;
import com.atcrew.recruit.JobWorkScheduleType;
import com.atcrew.recruit.RecruitService;
import com.atcrew.billing.internal.persistence.EntitlementBalanceRepository;
import com.atcrew.support.BillingTestSupport;
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
 * <p>실제 MariaDB·Elasticsearch Testcontainer와 전체 Spring 컨텍스트를 기동해
 * 필터 조합 검색과 최초 진입(결과 미노출) 상태의 요청/응답 구조를 REST Docs 스니펫으로 생성한다.
 */
class SearchApiDocTest extends RestDocsIntegrationSupport {

    @Autowired
    EntitlementBalanceRepository balanceRepository;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    RecruitService recruitService;

    @Autowired
    MediaCallbackService mediaCallbackService;

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
    void 구인글_유형_검색_문서화() throws Exception {
        String token = "token" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        publishedJobPosting(token + " 구인 공고");

        // RecruitSearchIndexer도 ArtworkSearchIndexer와 동일하게 @ApplicationModuleListener(비동기)라 폴링한다.
        awaitRecruitIndexed(token);

        mockMvc.perform(get("/api/search")
                        .param("q", token)
                        .param("postTypes", "JOB_POSTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].postType").value("JOB_POSTING"))
                .andDo(document("search/search-recruit",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("q").description("검색어").optional(),
                                parameterWithName("postTypes")
                                        .description("게시글 유형 필터 (PORTFOLIO·JOB_POSTING·JOB_SEEKING·TEAM_RECRUIT)")
                                        .optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("구인글 ID"),
                                fieldWithPath("data.items[].postType").description("게시글 유형 (JOB_POSTING)"),
                                fieldWithPath("data.items[].title").description("공고 제목"),
                                fieldWithPath("data.items[].authorName").description("작성자 표시명").optional(),
                                fieldWithPath("data.totalCount").description("전체 결과 건수"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    /** 작성 → 관리자 승인까지 마친 PUBLISHED 구인글을 만든다(커맨드의 submit=true라 저장 즉시 PENDING). */
    private void publishedJobPosting(String title) {
        String memberId = memberService.register(
                "search-recruit-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "searchrec" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                "검색문서기업").id();
        // 구인글은 유료 단건 게시 상품이다(구인구직-R02).
        BillingTestSupport.grantAllPostingProducts(balanceRepository, memberId);

        JobPostingInfo created = recruitService.createJobPosting(memberId, new CreateJobPostingCommand(
                title, "앳크루", "대표", "웹툰", "서울", "02-000-0000", "https://example.com",
                "회사 소개", true, true, false,
                List.of(ArtworkRole.TOTAL_ARTWORK), List.of(Genre.ROMANCE_FANTASY), "작업 범위", null, 2, "서류 → 면접",
                "무관", "신입", "무관", "무관",
                JobEmploymentType.FULL_TIME, JobWorkLocationType.OFFICE, JobWorkScheduleType.FIXED,
                null, null, true, true, true,
                JobPaymentType.ANNUAL_SALARY, JobPaymentUnit.ANNUAL, 3000L, 4000L, true,
                null, null, false, "복지 설명", List.of("식대"),
                "https://img.example/thumb.png", List.of("https://img.example/ref.png"), true));
        recruitService.approveJobPosting(created.id());
    }

    private void uploadReadyArtwork() throws InterruptedException {
        String memberId = memberService.register(
                "search-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "searchdoc" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                "검색문서작가").id();

        List<String> imageKeys = List.of("raw/" + UUID.randomUUID() + ".png");
        ArtworkInfo artwork = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                imageKeys, 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "검색문서화 작품", "설명",
                ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL, List.of(ArtworkRole.LINEART),
                List.of(Genre.BL), List.of("태그"),
                AgeRating.ALL, true, List.of(), List.of(), null, null, List.of(), List.of()
        ));

        // media webhook → MediaAssetProcessedEvent → artwork 리스너(비동기)로 READY 전환된다.
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artwork.id(), imageKeys.get(0),
                "thumb-key", null, "orig.avif", MediaProcessingStatus.DONE);
        awaitReady(memberId, artwork.id());
    }

    /** artwork 리스너는 @ApplicationModuleListener(비동기)라 READY 반영까지 폴링한다. */
    private void awaitReady(String memberId, String artworkId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            if (artworkService.getArtworkStatus(memberId, artworkId) == ArtworkStatus.READY) return;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("READY 전환 대기 시간 초과");
    }

    private void awaitIndexed() throws InterruptedException {
        // ArtworkSearchIndexer가 비동기로 색인을 반영할 시간을 확보한다
        Thread.sleep(Duration.ofSeconds(8).toMillis());
    }

    /** RecruitSearchIndexer의 @ApplicationModuleListener는 비동기라, 색인 반영까지 폴링한다. */
    private void awaitRecruitIndexed(String token) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            String body = mockMvc.perform(get("/api/search")
                            .param("q", token)
                            .param("postTypes", "JOB_POSTING"))
                    .andReturn().getResponse().getContentAsString();
            if (objectMapper.readTree(body).path("data").path("items").size() > 0) {
                return;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("색인 반영 대기 시간 초과");
    }
}
