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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BookmarkController 에러 응답 restdocs-api-spec 문서화 테스트.
 *
 * <p>{@code ArtworkErrorCode}의 코드 중 이 컨트롤러로 트리거 가능한 대표 케이스를 커버한다
 * — BOOKMARK_FOLDER_NOT_FOUND, BOOKMARK_FOLDER_DUPLICATE_NAME, BOOKMARK_ALREADY_EXISTS, BOOKMARK_NOT_FOUND.
 *
 * <p>BOOKMARK_FOLDER_NAME_BLANK는 {@code CreateBookmarkFolderRequest}의 {@code @NotBlank @Size(max=20)}
 * 빈 검증이 서비스 계층 도달 전에 이미 동일 조건(공백/20자 초과)을 전부 걸러내 공개 API로는 도달할 수
 * 없는 방어 코드라 커버하지 않는다.
 */
class BookmarkControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Autowired
    MediaCallbackService mediaCallbackService;

    @Test
    void 존재하지_않는_북마크_폴더_삭제시_404_BOOKMARK_FOLDER_NOT_FOUND() throws Exception {
        RegisteredMember member = registerMember("폴더없음유저");

        mockMvc.perform(delete("/api/bookmarks/folders/{folderId}", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKMARK_FOLDER_NOT_FOUND"))
                .andDo(document("bookmark/folder-delete-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 북마크 폴더 삭제 시도 — 404 BOOKMARK_FOLDER_NOT_FOUND")));
    }

    @Test
    void 중복된_이름으로_북마크_폴더_생성시_409_BOOKMARK_FOLDER_DUPLICATE_NAME() throws Exception {
        RegisteredMember member = registerMember("폴더중복유저");
        Map<String, Object> body = Map.of("name", "관심 작품");

        mockMvc.perform(post("/api/bookmarks/folders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/bookmarks/folders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKMARK_FOLDER_DUPLICATE_NAME"))
                .andDo(document("bookmark/folder-create-duplicate-name",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("이미 존재하는 폴더명으로 북마크 폴더 생성 시도 — 409 BOOKMARK_FOLDER_DUPLICATE_NAME")));
    }

    @Test
    void 이미_북마크한_작품_재저장시_409_BOOKMARK_ALREADY_EXISTS() throws Exception {
        RegisteredMember member = registerMember("북마크중복유저");
        String artworkId = uploadReadyArtwork(member.accessToken());
        Map<String, Object> body = Map.of("artworkId", artworkId);

        mockMvc.perform(post("/api/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKMARK_ALREADY_EXISTS"))
                .andDo(document("bookmark/save-already-exists",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("이미 북마크한 작품을 다시 저장 시도 — 409 BOOKMARK_ALREADY_EXISTS")));
    }

    @Test
    void 북마크하지_않은_작품_해제시_404_BOOKMARK_NOT_FOUND() throws Exception {
        RegisteredMember member = registerMember("북마크없음유저");

        mockMvc.perform(delete("/api/bookmarks/{artworkId}", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKMARK_NOT_FOUND"))
                .andDo(document("bookmark/remove-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("북마크하지 않은 작품을 해제 시도 — 404 BOOKMARK_NOT_FOUND")));
    }

    /** 작품을 업로드하고 이미지 처리 완료(READY)까지 만든다 — 북마크 저장은 READY 작품만 대상이다. */
    private String uploadReadyArtwork(String accessToken) throws Exception {
        String imageKey = "raw/" + UUID.randomUUID() + ".png";
        Map<String, Object> uploadBody = new LinkedHashMap<>();
        uploadBody.put("imageKeys", List.of(imageKey));
        uploadBody.put("representativeImageIndex", 0);
        uploadBody.put("imageLayoutType", "VERTICAL_SCROLL");
        uploadBody.put("title", "북마크용 작품");
        uploadBody.put("artworkField", "ILLUSTRATION");
        uploadBody.put("creativeType", "ORIGINAL");
        uploadBody.put("roles", List.of("LINEART"));
        uploadBody.put("ageRating", "ALL");
        uploadBody.put("visibility", "PUBLIC");

        MvcResult result = mockMvc.perform(post("/api/artworks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadBody)))
                .andExpect(status().isCreated())
                .andReturn();
        String artworkId = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();

        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                "thumb/test.avif", "thumb-adult/test.avif", "original/test.avif",
                MediaProcessingStatus.DONE);
        awaitReady(accessToken, artworkId);
        return artworkId;
    }

    /** artwork 리스너(@ApplicationModuleListener)가 비동기로 상태를 반영할 때까지 폴링한다. */
    private void awaitReady(String accessToken, String artworkId) throws Exception {
        java.time.Instant deadline = java.time.Instant.now().plus(java.time.Duration.ofSeconds(15));
        while (java.time.Instant.now().isBefore(deadline)) {
            MvcResult result = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .get("/api/artworks/{artworkId}/status", artworkId)
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andReturn();
            String data = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data").asText();
            if ("READY".equals(data)) return;
            Thread.sleep(200);
        }
        throw new AssertionError("작품이 READY로 전이되지 않았습니다: " + artworkId);
    }

    /** 이메일 회원가입으로 액세스 토큰·회원 ID를 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "bookmark-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
