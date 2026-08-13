package com.atcrew.artwork.docs;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ArtworkController 에러 응답 restdocs-api-spec 문서화 테스트.
 *
 * <p>{@code ArtworkErrorCode}의 코드 중 이 컨트롤러로 트리거 가능한 대표 케이스를 커버한다
 * — ARTWORK_NOT_FOUND, ARTWORK_ACCESS_DENIED, ARTWORK_DELETED, ARTWORK_PRIVATE, ARTWORK_NOT_READY,
 * INVALID_IMAGE_COUNT, INVALID_CONTENT_TYPE, INVALID_REPRESENTATIVE_INDEX, INVALID_CURSOR.
 */
class ArtworkControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Autowired
    MediaCallbackService mediaCallbackService;

    @Test
    void 존재하지_않는_작품_조회시_404_ARTWORK_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/artworks/{artworkId}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTWORK_NOT_FOUND"))
                .andDo(document("artwork/get-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 작품 ID로 상세 조회 시도 — 404 ARTWORK_NOT_FOUND")));
    }

    @Test
    void 본인_작품이_아닌_작품_상태_조회시_403_ARTWORK_ACCESS_DENIED() throws Exception {
        RegisteredMember owner = registerMember("작품주인");
        String artworkId = uploadArtwork(owner.accessToken(), "PUBLIC");
        RegisteredMember other = registerMember("남의작품조회자");

        mockMvc.perform(get("/api/artworks/{artworkId}/status", artworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ARTWORK_ACCESS_DENIED"))
                .andDo(document("artwork/status-access-denied",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("본인 작품이 아닌 작품의 처리 상태를 조회 시도 — 403 ARTWORK_ACCESS_DENIED")));
    }

    @Test
    void 휴지통_작품을_타인이_조회시_410_ARTWORK_DELETED() throws Exception {
        RegisteredMember owner = registerMember("삭제작품주인");
        String artworkId = uploadArtwork(owner.accessToken(), "PUBLIC");
        mockMvc.perform(delete("/api/artworks/{artworkId}", artworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());
        RegisteredMember other = registerMember("삭제작품조회자");

        mockMvc.perform(get("/api/artworks/{artworkId}", artworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ARTWORK_DELETED"))
                .andDo(document("artwork/get-deleted",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("휴지통에 있는 작품을 타인이 조회 시도 — 410 ARTWORK_DELETED")));
    }

    @Test
    void 비공개_작품을_타인이_조회시_403_ARTWORK_PRIVATE() throws Exception {
        RegisteredMember owner = registerMember("비공개작품주인");
        String imageKey = "raw/" + UUID.randomUUID() + ".png";
        String artworkId = uploadArtwork(owner.accessToken(), "PRIVATE", imageKey);
        // 이미지 처리 완료(READY)까지 만들어야 타인 접근 판정이 PRIVATE로 떨어진다 — PROCESSING이면 NOT_FOUND다.
        // artwork 리스너는 @ApplicationModuleListener(비동기)라 상태 반영까지 폴링한다.
        markImageDone(artworkId, imageKey);
        awaitReady(owner.accessToken(), artworkId);
        RegisteredMember other = registerMember("비공개작품조회자");

        mockMvc.perform(get("/api/artworks/{artworkId}", artworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ARTWORK_PRIVATE"))
                .andDo(document("artwork/get-private",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("비공개(PRIVATE)이고 라이브 포트폴리오에 없는 작품을 타인이 조회 시도 — 403 ARTWORK_PRIVATE")));
    }

    @Test
    void 이미지_처리중인_작품_공개상태_변경시_400_ARTWORK_NOT_READY() throws Exception {
        RegisteredMember owner = registerMember("처리중작품주인");
        String artworkId = uploadArtwork(owner.accessToken(), "PUBLIC");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("visibility", "PRIVATE");

        mockMvc.perform(patch("/api/artworks/{artworkId}/visibility", artworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARTWORK_NOT_READY"))
                .andDo(document("artwork/visibility-not-ready",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("이미지 처리 중(PROCESSING)인 작품의 공개 상태 변경 시도 — 400 ARTWORK_NOT_READY")));
    }

    @Test
    void presign_개수와_형식목록_길이불일치시_400_INVALID_IMAGE_COUNT() throws Exception {
        RegisteredMember member = registerMember("presign개수불일치");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", 2);
        body.put("contentTypes", List.of("image/jpeg"));

        mockMvc.perform(post("/api/artwork/images/presign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE_COUNT"))
                .andDo(document("artwork/presign-invalid-count",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("count와 contentTypes 길이가 일치하지 않는 presign 요청 — 400 INVALID_IMAGE_COUNT")));
    }

    @Test
    void presign_허용되지_않는_이미지_형식이면_400_INVALID_CONTENT_TYPE() throws Exception {
        RegisteredMember member = registerMember("presign형식오류");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", 1);
        body.put("contentTypes", List.of("image/gif"));

        mockMvc.perform(post("/api/artwork/images/presign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTENT_TYPE"))
                .andDo(document("artwork/presign-invalid-content-type",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("허용되지 않는 이미지 형식(image/gif)으로 presign 요청 — 400 INVALID_CONTENT_TYPE")));
    }

    @Test
    void 작품_업로드시_대표이미지_인덱스가_범위밖이면_400_INVALID_REPRESENTATIVE_INDEX() throws Exception {
        RegisteredMember member = registerMember("대표인덱스오류");

        mockMvc.perform(post("/api/artworks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadBody("PUBLIC", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPRESENTATIVE_INDEX"))
                .andDo(document("artwork/upload-invalid-representative-index",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("imageKeys 범위를 벗어난 대표 이미지 인덱스로 작품 업로드 시도 — 400 INVALID_REPRESENTATIVE_INDEX")));
    }

    @Test
    void 내_작품_목록_커서_형식_오류시_400_INVALID_CURSOR() throws Exception {
        RegisteredMember member = registerMember("커서오류");

        mockMvc.perform(get("/api/members/me/artworks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .param("cursor", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
                .andDo(document("artwork/my-artworks-invalid-cursor",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("형식이 잘못된 커서로 내 작품 목록 조회 시도 — 400 INVALID_CURSOR")));
    }

    /** Worker webhook 1건을 재현해 이미지 처리를 완료시키고 작품을 READY로 전이시킨다. */
    private void markImageDone(String artworkId, String imageKey) {
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                "thumb/test.avif", "thumb-adult/test.avif", "original/test.avif",
                MediaProcessingStatus.DONE);
    }

    /** artwork 리스너(@ApplicationModuleListener)가 비동기로 상태를 반영할 때까지 폴링한다. */
    private void awaitReady(String accessToken, String artworkId) throws Exception {
        java.time.Instant deadline = java.time.Instant.now().plus(java.time.Duration.ofSeconds(15));
        while (java.time.Instant.now().isBefore(deadline)) {
            MvcResult result = mockMvc.perform(get("/api/artworks/{artworkId}/status", artworkId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andReturn();
            String data = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data").asText();
            if ("READY".equals(data)) return;
            Thread.sleep(200);
        }
        throw new AssertionError("작품이 READY로 전이되지 않았습니다: " + artworkId);
    }

    private String uploadArtwork(String accessToken, String visibility) throws Exception {
        return uploadArtwork(accessToken, visibility, "raw/" + UUID.randomUUID() + ".png");
    }

    private String uploadArtwork(String accessToken, String visibility, String imageKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/artworks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadBody(visibility, 0, imageKey))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
    }

    private Map<String, Object> uploadBody(String visibility, int representativeImageIndex) {
        return uploadBody(visibility, representativeImageIndex, "raw/" + UUID.randomUUID() + ".png");
    }

    private Map<String, Object> uploadBody(String visibility, int representativeImageIndex, String imageKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("imageKeys", List.of(imageKey));
        body.put("representativeImageIndex", representativeImageIndex);
        body.put("imageLayoutType", "VERTICAL_SCROLL");
        body.put("title", "테스트 작품");
        body.put("artworkField", "ILLUSTRATION");
        body.put("creativeType", "ORIGINAL");
        body.put("roles", List.of("LINEART"));
        body.put("ageRating", "ALL");
        body.put("visibility", visibility);
        return body;
    }

    /** 이메일 회원가입으로 액세스 토큰·회원 ID를 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "artwork-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new RegisteredMember(
                objectMapper.readTree(body).at("/data/accessToken").asText(),
                objectMapper.readTree(body).at("/data/member/id").asText());
    }

    private record RegisteredMember(String accessToken, String memberId) {
    }

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
            String countryCode
    ) {}
}
