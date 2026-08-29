package com.atcrew.portfolio.docs;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.WorkDuration;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.member.Language;
import com.atcrew.support.BillingTestSupport;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 포트폴리오 API 문서화 통합 테스트 (docs/design/portfolio-module-design.md §4).
 *
 * <p>작품은 presign → R2 업로드 HTTP 플로우 대신 {@link ArtworkService}를 직접 호출해 준비한다
 * (ApplicationApiDocTest가 RecruitService를 직접 쓰는 것과 동일한 패턴). 포트폴리오 편입 검증은
 * DELETED만 거르므로 대부분의 문서화는 PROCESSING 상태 그대로 진행하고, 공유 목록 문서화만
 * media webhook 경로로 READY까지 올린다 — 비인증 공유 목록은 처리 완료된 작품만 노출한다(§5.4).
 *
 * <p>공유 포트폴리오는 프로 전용이라 billing 웹훅 대신 구독 행을 직접 만들어 플랜을 승급한다
 * (PortfolioServiceTests와 동일).
 */
class PortfolioApiDocTest extends RestDocsIntegrationSupport {

    @Autowired
    ArtworkService artworkService;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    // Worker webhook 도달 지점 — 공유 목록 문서화용 작품을 READY까지 올리는 데 쓴다.
    @Autowired
    MediaCallbackService mediaCallbackService;

    @Test
    void 공유_포트폴리오_생성_조회_수정_삭제_문서화() throws Exception {
        RegisteredMember member = registerProMember("포트폴리오생성유저");
        String firstArtworkId = uploadArtwork(member.memberId(), "첫 번째 작품");
        String secondArtworkId = uploadArtwork(member.memberId(), "두 번째 작품");
        String thirdArtworkId = uploadArtwork(member.memberId(), "세 번째 작품");
        String fourthArtworkId = uploadArtwork(member.memberId(), "네 번째 작품");

        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("title", "일러스트 모음");
        createBody.put("reflectionType", "LIVE");
        createBody.put("artworkIds", List.of(firstArtworkId, secondArtworkId));

        MvcResult createResult = mockMvc.perform(post("/api/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.kind").value("SHARED"))
                .andExpect(jsonPath("$.data.reflectionType").value("LIVE"))
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andDo(document("portfolio/create-shared-live",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("title").description("공유 포트폴리오 제목 (필수, 최대 100자)"),
                                fieldWithPath("reflectionType")
                                        .description("반영 유형 — LIVE(최신 반영형) / SNAPSHOT(고정형). 생성 후 전환 불가"),
                                fieldWithPath("artworkIds")
                                        .description("담을 작품 ID 목록 (최소 2개, 상한 없음). 본인 소유 작품만 담을 수 있다")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("포트폴리오 ID"),
                                fieldWithPath("data.kind").description("유형 — ARTIST_PAGE(작가 페이지) / SHARED(공유)"),
                                fieldWithPath("data.reflectionType").description("반영 유형 — LIVE / SNAPSHOT"),
                                fieldWithPath("data.title").type(JsonFieldType.STRING)
                                        .description("제목 — 작가 페이지는 null").optional(),
                                fieldWithPath("data.shareSlug").type(JsonFieldType.STRING)
                                        .description("공유 링크 슬러그 (22자) — 작가 페이지는 null").optional(),
                                fieldWithPath("data.itemCount").description("담긴 작품 수"),
                                fieldWithPath("data.artworks[].artworkId").type(JsonFieldType.STRING)
                                        .description("원본 작품 ID — 고정형 카드는 null").optional(),
                                fieldWithPath("data.artworks[].snapshotId").type(JsonFieldType.STRING)
                                        .description("고정형 스냅샷 ID — 최신 반영형·작가 페이지 카드는 null").optional(),
                                fieldWithPath("data.artworks[].title").description("작품 제목"),
                                fieldWithPath("data.artworks[].thumbKey").type(JsonFieldType.STRING)
                                        .description("카드 썸네일 R2 키 — 이미지 처리 전이면 null").optional(),
                                fieldWithPath("data.artworks[].thumbAdultKey").type(JsonFieldType.STRING)
                                        .description("성인 블러 썸네일 R2 키 — 사용자 지정 썸네일을 쓰면 null").optional(),
                                fieldWithPath("data.artworks[].ageRating").description("연령 등급 (ALL·ADULT)"),
                                fieldWithPath("data.artworks[].artworkField").description("작품 분야"),
                                fieldWithPath("data.artworks[].visibility")
                                        .description("원본 작품의 피드 공개 여부 (PUBLIC=공개 ON, PRIVATE=공개 OFF)"),
                                fieldWithPath("data.artworks[].createdAt")
                                        .description("원본 작품 등록 시각 (ISO 8601) — 포트폴리오 내 정렬 기준"),
                                fieldWithPath("data.createdAt").description("생성 시각 (ISO 8601)"),
                                fieldWithPath("data.updatedAt").description("최종 수정 시각 (ISO 8601)")
                        )
                ))
                .andReturn();
        String portfolioId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        mockMvc.perform(get("/api/portfolios/{portfolioId}", portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(portfolioId))
                .andExpect(jsonPath("$.data.artworks[0].artworkId").value(firstArtworkId))
                .andDo(document("portfolio/get-portfolio",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("portfolioId").description("포트폴리오 ID (UUID)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("포트폴리오 ID"),
                                fieldWithPath("data.kind").description("유형 — ARTIST_PAGE / SHARED"),
                                fieldWithPath("data.reflectionType").description("반영 유형 — LIVE / SNAPSHOT"),
                                fieldWithPath("data.title").type(JsonFieldType.STRING)
                                        .description("제목 — 작가 페이지는 null").optional(),
                                fieldWithPath("data.shareSlug").type(JsonFieldType.STRING)
                                        .description("공유 링크 슬러그 — 작가 페이지는 null").optional(),
                                fieldWithPath("data.itemCount").description("담긴 작품 수"),
                                fieldWithPath("data.artworks[].artworkId").description("원본 작품 ID"),
                                fieldWithPath("data.artworks[].title").description("작품 제목"),
                                fieldWithPath("data.artworks[].visibility").description("원본 작품의 공개 범위"),
                                fieldWithPath("data.createdAt").description("생성 시각 (ISO 8601)"),
                                fieldWithPath("data.updatedAt").description("최종 수정 시각 (ISO 8601)")
                        )
                ));

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("title", "일러스트 모음 (수정)");
        updateBody.put("artworkIds", List.of(secondArtworkId, fourthArtworkId));

        mockMvc.perform(patch("/api/portfolios/{portfolioId}", portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("일러스트 모음 (수정)"))
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.artworks[0].artworkId").value(secondArtworkId))
                .andDo(document("portfolio/update-portfolio",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("portfolioId").description("포트폴리오 ID (UUID)")
                        ),
                        requestFields(
                                fieldWithPath("title")
                                        .description("변경할 제목 (최대 100자). null이면 유지, 작가 페이지에 값을 보내면 400").optional(),
                                fieldWithPath("artworkIds")
                                        .description("구성 작품 ID 목록 (상한 없음). null이면 유지, 빈 배열이면 전부 비운다(작가 페이지만). "
                                                + "공유 포트폴리오는 2개 미만으로 줄이면 400").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("포트폴리오 ID"),
                                fieldWithPath("data.title").description("변경된 제목"),
                                fieldWithPath("data.itemCount").description("변경된 구성의 작품 수"),
                                fieldWithPath("data.artworks[].artworkId").description("구성 작품 ID (업로드순 고정)"),
                                fieldWithPath("data.updatedAt").description("최종 수정 시각 (ISO 8601)")
                        )
                ));

        mockMvc.perform(post("/api/portfolios/{portfolioId}/artworks", portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("artworkIds", List.of(thirdArtworkId)))))
                .andExpect(status().isNoContent())
                .andDo(document("portfolio/add-artworks",
                        preprocessRequest(prettyPrint()),
                        pathParameters(
                                parameterWithName("portfolioId").description("포트폴리오 ID (UUID)")
                        ),
                        requestFields(
                                fieldWithPath("artworkIds")
                                        .description("추가할 작품 ID 목록 (1개 이상, 개수 상한 없음). 이미 담긴 작품은 무시된다")
                        )
                ));

        mockMvc.perform(delete("/api/portfolios/{portfolioId}/artworks/{artworkId}", portfolioId, thirdArtworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isNoContent())
                .andDo(document("portfolio/remove-artwork",
                        pathParameters(
                                parameterWithName("portfolioId").description("포트폴리오 ID (UUID)"),
                                parameterWithName("artworkId").description("제거할 작품 ID (UUID)")
                        )
                ));

        mockMvc.perform(delete("/api/portfolios/{portfolioId}", portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isNoContent())
                .andDo(document("portfolio/delete-portfolio",
                        pathParameters(
                                parameterWithName("portfolioId").description("포트폴리오 ID (UUID). 작가 페이지는 삭제할 수 없다")
                        )
                ));

        mockMvc.perform(get("/api/portfolios/{portfolioId}", portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    void 고정형_포트폴리오_생성과_수정_거부_문서화() throws Exception {
        RegisteredMember member = registerProMember("고정형생성유저");
        String artworkId = uploadArtwork(member.memberId(), "고정형 작품");
        String secondArtworkId = uploadArtwork(member.memberId(), "고정형 작품 2");

        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("title", "2026 상반기 아카이브");
        createBody.put("reflectionType", "SNAPSHOT");
        createBody.put("artworkIds", List.of(artworkId, secondArtworkId));

        MvcResult createResult = mockMvc.perform(post("/api/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reflectionType").value("SNAPSHOT"))
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andDo(document("portfolio/create-shared-snapshot",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("title").description("공유 포트폴리오 제목 (필수, 최대 100자)"),
                                fieldWithPath("reflectionType")
                                        .description("SNAPSHOT — 생성 시점 작품 표시 정보와 작성자 이름을 함께 얼린다"),
                                fieldWithPath("artworkIds").description("담을 작품 ID 목록 (최소 2개, 상한 없음)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("포트폴리오 ID"),
                                fieldWithPath("data.reflectionType").description("반영 유형 (SNAPSHOT)"),
                                fieldWithPath("data.shareSlug").description("공유 링크 슬러그 (22자)"),
                                fieldWithPath("data.itemCount").description("얼려 담긴 작품 수"),
                                fieldWithPath("data.artworks[].snapshotId")
                                        .description("스냅샷 ID — 스냅샷 상세 URL의 식별자. 원본 작품 ID는 노출하지 않는다"),
                                fieldWithPath("data.artworks[].artworkId").type(JsonFieldType.STRING)
                                        .description("고정형 카드는 항상 null — 원본 작품 URL 조립 근거로 쓰지 않는다").optional(),
                                fieldWithPath("data.artworks[].title").description("생성 시점의 작품 제목"),
                                fieldWithPath("data.artworks[].visibility")
                                        .description("스냅샷 카드의 공개 범위 — 원본 비공개 전환에 영향받지 않고 항상 PUBLIC")
                        )
                ))
                .andReturn();
        String snapshotPortfolioId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();
        String shareSlug = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/shareSlug").asText();
        String snapshotId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/artworks/0/snapshotId").asText();

        // 스냅샷 상세는 인증 없이 열리는 독립 자원이다(마이페이지_작가-R39·R42).
        mockMvc.perform(get("/api/portfolios/shared/{identifier}/snapshots/{snapshotId}", shareSlug, snapshotId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(jsonPath("$.data.snapshotId").value(snapshotId))
                .andDo(document("portfolio/get-shared-snapshot-detail",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("identifier").description("공유 슬러그 (22자) 또는 작가 handle"),
                                parameterWithName("snapshotId").description("스냅샷 ID (UUID) — 카드 응답의 snapshotId")
                        ),
                        responseHeaders(
                                headerWithName("X-Robots-Tag").description("검색엔진 색인 제외 (noindex)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.snapshotId").description("스냅샷 ID"),
                                fieldWithPath("data.title").description("생성 시점의 작품 제목"),
                                fieldWithPath("data.images[].originalKey").description("생성 당시 원본 이미지 R2 키"),
                                fieldWithPath("data.images[].thumbKey").type(JsonFieldType.STRING)
                                        .description("생성 당시 썸네일 R2 키 — 이미지 처리 전이면 null").optional(),
                                fieldWithPath("data.representativeImageIndex").description("대표 이미지 인덱스"),
                                fieldWithPath("data.tags").description("생성 시점의 태그"),
                                fieldWithPath("data.tools").description("생성 시점의 사용 툴"),
                                fieldWithPath("data.roles").description("생성 시점의 담당 역할"),
                                fieldWithPath("data.genres").description("생성 시점의 장르"),
                                fieldWithPath("data.videoLinks").description("생성 시점의 영상 링크"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING)
                                        .description("생성 시점의 작품 설명").optional(),
                                fieldWithPath("data.ageRating").description("연령 등급 (ALL·ADULT)"),
                                fieldWithPath("data.artworkField").description("작품 분야"),
                                fieldWithPath("data.sourceCreatedAt").description("원본 작품 등록 시각 (ISO 8601)"),
                                fieldWithPath("data.ownerName").description("작성자 이름 — 포트폴리오 생성 시점에 얼린 값")
                        )
                ));

        // 고정형은 어떤 수정도 받지 않는다(§5.2) — 실패 응답도 문서로 남긴다.
        mockMvc.perform(patch("/api/portfolios/{portfolioId}", snapshotPortfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "제목 변경 시도"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SNAPSHOT_PORTFOLIO_IMMUTABLE"))
                .andDo(document("portfolio/update-portfolio-snapshot-conflict",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("portfolioId").description("고정형 포트폴리오 ID (UUID)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("에러 코드 (SNAPSHOT_PORTFOLIO_IMMUTABLE)"),
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    void 내_포트폴리오_목록_문서화() throws Exception {
        RegisteredMember member = registerProMember("포트폴리오목록유저");
        List<String> fillerArtworkIds = List.of(
                uploadArtwork(member.memberId(), "채움 작품 1"), uploadArtwork(member.memberId(), "채움 작품 2"));
        createSharedPortfolio(member.accessToken(), "최신 반영형 포트폴리오", "LIVE", fillerArtworkIds);
        createSharedPortfolio(member.accessToken(), "고정형 포트폴리오", "SNAPSHOT", fillerArtworkIds);

        // 작가 페이지는 이 조회 시점에 lazy 생성되므로 공유 2건과 함께 3건이 나온다(§2.5).
        mockMvc.perform(get("/api/portfolios/me")
                        .param("sort", "LATEST")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andDo(document("portfolio/list-my-portfolios",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("kind").description("유형 필터 — ARTIST_PAGE / SHARED").optional(),
                                parameterWithName("reflectionType").description("반영 유형 필터 — LIVE / SNAPSHOT").optional(),
                                parameterWithName("sort").description("정렬 — OLDEST / LATEST / UPDATED (기본 LATEST)").optional(),
                                parameterWithName("cursor")
                                        .description("커서 — 직전 페이지 응답의 nextCursor를 그대로 전달한다(내부 형식은 보장하지 않는다)")
                                        .optional(),
                                parameterWithName("size").description("페이지 크기 (기본 20, 최대 50)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("포트폴리오 ID"),
                                fieldWithPath("data.items[].kind").description("유형 — ARTIST_PAGE / SHARED"),
                                fieldWithPath("data.items[].reflectionType").description("반영 유형 — LIVE / SNAPSHOT"),
                                fieldWithPath("data.items[].title").type(JsonFieldType.STRING)
                                        .description("제목 — 작가 페이지는 null(사용자 이름 헤더를 쓴다)").optional(),
                                fieldWithPath("data.items[].shareSlug").type(JsonFieldType.STRING)
                                        .description("공유 링크 슬러그 — 작가 페이지는 null").optional(),
                                fieldWithPath("data.items[].itemCount").description("담긴 작품 수 (카드의 \"N개\" 표기용)"),
                                fieldWithPath("data.items[].coverThumbnails[].thumbKey").type(JsonFieldType.STRING)
                                        .description("카드 커버 2x2 썸네일 R2 키 — 업로드 오래된순 최대 4개, "
                                                + "부족하면 있는 만큼만(빈 칸 처리는 클라이언트 담당). 이미지 처리 전이면 null")
                                        .optional(),
                                fieldWithPath("data.items[].coverThumbnails[].thumbAdultKey")
                                        .type(JsonFieldType.STRING)
                                        .description("커버 성인 블러 썸네일 R2 키 — 사용자 지정 썸네일을 쓰면 null").optional(),
                                fieldWithPath("data.items[].createdAt").description("생성 시각 (ISO 8601)"),
                                fieldWithPath("data.items[].updatedAt")
                                        .description("최종 변경 시각 (ISO 8601) — 작품 추가/제거나 원본 변경에 따른 "
                                                + "구성 재계산 같은 시스템 변경도 포함한다"),
                                fieldWithPath("data.items[].lastEditedAt")
                                        .description("[수정하기]로 저장한 시각 (ISO 8601) — sort=UPDATED의 정렬 기준"),
                                fieldWithPath("data.nextCursor").type(JsonFieldType.STRING)
                                        .description("다음 페이지 커서 (없으면 null)").optional(),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 유형 필터 — 공유 포트폴리오만 남는다
        mockMvc.perform(get("/api/portfolios/me")
                        .param("kind", "SHARED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void 선택_가능한_포트폴리오_목록_문서화() throws Exception {
        RegisteredMember member = registerProMember("선택목록유저");
        List<String> fillerArtworkIds = List.of(
                uploadArtwork(member.memberId(), "채움 작품 1"), uploadArtwork(member.memberId(), "채움 작품 2"));
        createSharedPortfolio(member.accessToken(), "최신 반영형 포트폴리오", "LIVE", fillerArtworkIds);
        createSharedPortfolio(member.accessToken(), "고정형 포트폴리오", "SNAPSHOT", fillerArtworkIds);

        // 작가 페이지 + 최신 반영형만 나오고 고정형은 제외된다(§4).
        mockMvc.perform(get("/api/portfolios/selectable")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].kind").value("ARTIST_PAGE"))
                .andExpect(jsonPath("$.data[1].title").value("최신 반영형 포트폴리오"))
                .andDo(document("portfolio/list-selectable-portfolios",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data[].id").description("포트폴리오 ID"),
                                fieldWithPath("data[].kind")
                                        .description("유형 — ARTIST_PAGE가 항상 맨 앞에 온다"),
                                fieldWithPath("data[].title").type(JsonFieldType.STRING)
                                        .description("제목 — 작가 페이지는 null").optional(),
                                fieldWithPath("data[].itemCount").description("담긴 작품 수")
                        )
                ));
    }

    @Test
    void 복제_원본_조회_문서화() throws Exception {
        RegisteredMember member = registerProMember("복제원본유저");
        String keptArtworkId = uploadArtwork(member.memberId(), "유지되는 작품");
        String deletedArtworkId = uploadArtwork(member.memberId(), "휴지통 갈 작품");
        String portfolioId = createSharedPortfolio(member.accessToken(), "복제 원본 포트폴리오", "LIVE",
                List.of(keptArtworkId, deletedArtworkId));

        // 휴지통으로 옮긴 작품은 자동 선택에서 빠지고 개수만 알려준다(§5.3).
        artworkService.deleteArtwork(member.memberId(), deletedArtworkId);

        mockMvc.perform(get("/api/portfolios/{portfolioId}/duplication-source", portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTitle").value("복제 원본 포트폴리오 복사본"))
                .andExpect(jsonPath("$.data.selectedArtworkIds.length()").value(1))
                .andExpect(jsonPath("$.data.excludedCount").value(1))
                .andDo(document("portfolio/get-duplication-source",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("portfolioId").description("복제할 원본 포트폴리오 ID (UUID)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.defaultTitle")
                                        .description("복제본 기본 제목 — \"{원본 제목} 복사본\", 작가 페이지는 \"{사용자 이름} 복사본\""),
                                fieldWithPath("data.selectedArtworkIds")
                                        .description("자동 선택될 작품 ID 목록 (원본 구성 순서 유지, 0개여도 복제 진행 가능)"),
                                fieldWithPath("data.excludedCount")
                                        .description("삭제·비공개라 자동 선택에서 빠진 작품 수")
                        )
                ));
    }

    @Test
    void 공유_링크_열람_문서화() throws Exception {
        RegisteredMember member = registerProMember("공유열람유저");
        String artworkId = uploadArtwork(member.memberId(), "공유 링크 작품");
        String secondArtworkId = uploadArtwork(member.memberId(), "공유 링크 작품 2");

        MvcResult createResult = mockMvc.perform(post("/api/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequestBody(
                                "공유용 포트폴리오", "LIVE", List.of(artworkId, secondArtworkId)))))
                .andExpect(status().isCreated())
                .andReturn();
        String shareSlug = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/shareSlug").asText();

        // 슬러그 식별자 — 인증 없이 열람한다
        mockMvc.perform(get("/api/portfolios/shared/{identifier}", shareSlug))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(jsonPath("$.data.kind").value("SHARED"))
                .andExpect(jsonPath("$.data.title").value("공유용 포트폴리오"))
                .andExpect(jsonPath("$.data.ownerName").value("공유열람유저"))
                .andDo(document("portfolio/get-shared-portfolio",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("identifier").description("공유 슬러그(22자) 또는 작가 handle — 슬러그를 먼저 해석한다")
                        ),
                        responseHeaders(
                                headerWithName("X-Robots-Tag").description("검색엔진 색인 제외 (noindex)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("포트폴리오 ID"),
                                fieldWithPath("data.kind").description("유형 — ARTIST_PAGE / SHARED"),
                                fieldWithPath("data.reflectionType").description("반영 유형 — LIVE / SNAPSHOT"),
                                fieldWithPath("data.title").type(JsonFieldType.STRING)
                                        .description("제목 — 작가 페이지는 null").optional(),
                                fieldWithPath("data.ownerName")
                                        .description("헤더용 작성자 이름 — 고정형은 생성 시점에 얼린 이름, 그 외는 현재 이름"),
                                fieldWithPath("data.itemCount").description("담긴 작품 수"),
                                fieldWithPath("data.createdAt").description("생성 시각 (ISO 8601)"),
                                fieldWithPath("data.updatedAt").description("최종 수정 시각 (ISO 8601)")
                        )
                ));

        // handle 식별자 — 작가 페이지가 열린다. 작가 페이지는 목록 조회 시점에 lazy 생성된다.
        mockMvc.perform(get("/api/portfolios/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portfolios/shared/{identifier}", member.handle()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(jsonPath("$.data.kind").value("ARTIST_PAGE"))
                .andExpect(jsonPath("$.data.title").isEmpty())
                .andExpect(jsonPath("$.data.ownerName").value("공유열람유저"))
                .andDo(document("portfolio/get-shared-artist-page",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("identifier").description("작가 handle — 슬러그로 찾지 못하면 작가 페이지로 해석한다")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("작가 페이지 포트폴리오 ID"),
                                fieldWithPath("data.kind").description("유형 (ARTIST_PAGE)"),
                                fieldWithPath("data.ownerName").description("헤더에 표시할 작가 이름"),
                                fieldWithPath("data.itemCount").description("담긴 작품 수")
                        )
                ));

        mockMvc.perform(get("/api/portfolios/shared/{identifier}", "no-such-identifier"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"))
                .andDo(document("portfolio/get-shared-portfolio-not-found", preprocessResponse(prettyPrint())));
    }

    @Test
    void 공유_포트폴리오_작품_목록_문서화() throws Exception {
        RegisteredMember member = registerProMember("공유작품목록유저");
        String firstArtworkId = uploadReadyArtwork(member.memberId(), "첫 번째 작품");
        String secondArtworkId = uploadReadyArtwork(member.memberId(), "두 번째 작품");
        String thirdArtworkId = uploadReadyArtwork(member.memberId(), "세 번째 작품");

        MvcResult createResult = mockMvc.perform(post("/api/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequestBody(
                                "작품 목록 포트폴리오", "LIVE",
                                List.of(firstArtworkId, secondArtworkId, thirdArtworkId)))))
                .andExpect(status().isCreated())
                .andReturn();
        String shareSlug = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/shareSlug").asText();

        MvcResult firstPage = mockMvc.perform(get("/api/portfolios/shared/{identifier}/artworks", shareSlug)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].artworkId").value(firstArtworkId))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andDo(document("portfolio/list-shared-portfolio-artworks",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("identifier").description("공유 슬러그 또는 작가 handle")
                        ),
                        queryParameters(
                                parameterWithName("cursor").description("커서 — 직전 페이지 마지막 항목의 순번").optional(),
                                parameterWithName("size").description("페이지 크기 (기본 20, 최대 50)").optional()
                        ),
                        responseHeaders(
                                headerWithName("X-Robots-Tag").description("검색엔진 색인 제외 (noindex)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].artworkId").type(JsonFieldType.STRING)
                                        .description("원본 작품 ID — 고정형 카드는 null").optional(),
                                fieldWithPath("data.items[].snapshotId").type(JsonFieldType.STRING)
                                        .description("고정형 스냅샷 ID — 최신 반영형·작가 페이지 카드는 null").optional(),
                                fieldWithPath("data.items[].title").description("작품 제목"),
                                fieldWithPath("data.items[].thumbKey").type(JsonFieldType.STRING)
                                        .description("카드 썸네일 R2 키 — 이미지 처리 전이면 null").optional(),
                                fieldWithPath("data.items[].thumbAdultKey").type(JsonFieldType.STRING)
                                        .description("성인 블러 썸네일 R2 키").optional(),
                                fieldWithPath("data.items[].ageRating").description("연령 등급 (ALL·ADULT)"),
                                fieldWithPath("data.items[].artworkField").description("작품 분야"),
                                fieldWithPath("data.items[].visibility").description("원본 작품의 공개 범위"),
                                fieldWithPath("data.items[].createdAt").description("원본 작품 등록 시각 (ISO 8601)"),
                                fieldWithPath("data.nextCursor").type(JsonFieldType.STRING)
                                        .description("다음 페이지 커서 (없으면 null)").optional(),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ))
                .andReturn();
        String nextCursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .at("/data/nextCursor").asText();

        mockMvc.perform(get("/api/portfolios/shared/{identifier}/artworks", shareSlug)
                        .param("cursor", nextCursor)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].artworkId").value(thirdArtworkId))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 스타터_계정_공유_포트폴리오_생성_403() throws Exception {
        RegisteredMember member = registerMember("스타터유저");

        mockMvc.perform(post("/api/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequestBody(
                                "스타터가 만드는 포트폴리오", "LIVE", List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRO_PLAN_REQUIRED"))
                .andDo(document("portfolio/create-shared-starter-forbidden",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("에러 코드 (PRO_PLAN_REQUIRED)"),
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰·회원 ID·handle을 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "portfolio-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR", "KO"))))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new RegisteredMember(
                objectMapper.readTree(body).at("/data/accessToken").asText(),
                objectMapper.readTree(body).at("/data/member/id").asText(),
                objectMapper.readTree(body).at("/data/member/handle").asText());
    }

    /** 공유 포트폴리오는 프로 전용이므로 구독 행을 직접 만들어 플랜을 승급한다. */
    private RegisteredMember registerProMember(String name) throws Exception {
        RegisteredMember member = registerMember(name);
        BillingTestSupport.grantProPlan(subscriptionRepository, member.memberId());
        return member;
    }

    /** presign·R2 업로드 HTTP 플로우 대신 ArtworkService를 직접 호출해 작품을 준비한다. */
    private String uploadArtwork(String memberId, String title) {
        return uploadArtwork(memberId, title, "raw/" + UUID.randomUUID() + ".png");
    }

    private String uploadArtwork(String memberId, String title, String imageKey) {
        return artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(imageKey), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                title, "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART), List.of(Genre.FANTASY), null, List.of("태그"),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of("clip studio"),
                new WorkDuration(1, 1, 1, 1), 1, List.of(), List.of())).id();
    }

    /** 비인증 공유 목록은 처리 완료된 작품만 노출하므로(§5.4) media webhook 경로로 READY까지 올린다. */
    private String uploadReadyArtwork(String memberId, String title) {
        String imageKey = "raw/" + UUID.randomUUID() + ".png";
        String artworkId = uploadArtwork(memberId, title, imageKey);
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                "thumb", null, "avif", MediaProcessingStatus.DONE);
        awaitReady(memberId, artworkId);
        return artworkId;
    }

    /** artwork 리스너는 @ApplicationModuleListener(비동기)라 READY 반영까지 폴링한다. */
    private void awaitReady(String memberId, String artworkId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
        while (Instant.now().isBefore(deadline)) {
            if (artworkService.getArtworkStatus(memberId, artworkId) == ArtworkStatus.READY) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("READY 전환 대기 시간 초과");
    }

    /** 공유 포트폴리오를 생성하고 portfolioId를 반환한다. */
    private String createSharedPortfolio(String accessToken, String title, String reflectionType,
                                         List<String> artworkIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createRequestBody(title, reflectionType, artworkIds))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
    }

    private Map<String, Object> createRequestBody(String title, String reflectionType, List<String> artworkIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("reflectionType", reflectionType);
        body.put("artworkIds", artworkIds);
        return body;
    }

    /** 회원가입으로 발급받은 액세스 토큰·회원 ID·handle */
    private record RegisteredMember(
            String accessToken, // 액세스 토큰
            String memberId,    // 회원 ID
            String handle       // 작가 handle — 작가 페이지 공유 링크 식별자로 쓴다
    ) {
    }

    /** 이메일 회원가입 요청 바디 (accessToken 발급용) */
    record RegisterRequest(
            String email,
            String password,
            String passwordConfirm,
            String name,
            boolean agreeService,
            boolean agreePrivacy,
            boolean agreeThirdParty,
            boolean agreeMarketing,
            String timezone,
            String countryCode,
            String primaryLanguage
    ) {}
}
