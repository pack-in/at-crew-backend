package com.atcrew.artwork.docs;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.media.internal.application.MediaKeySigner;
import com.atcrew.member.Language;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 작품 상세의 저장 버튼용 API 문서화 통합 테스트 — 상세 응답의 bookmarkCount와
 * 현재 회원의 저장 여부(GET /api/bookmarks/{artworkId}).
 *
 * <p>상세 GET은 FE SSR이 토큰 없이 부르므로 회원마다 다른 저장 여부는 상세 응답이 아니라 별도 API로 준다.
 */
class BookmarkStatusApiDocTest extends RestDocsIntegrationSupport {

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 저장하면_상세의_북마크_수가_오르고_해제하면_내려간다() throws Exception {
        ArtworkInfo artwork = publishReady(registerAuthor().id());
        String saverToken = registerMemberAndGetToken("저장회원");
        String otherToken = registerMemberAndGetToken("다른회원");

        mockMvc.perform(get("/api/artworks/{artworkId}", artwork.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarkCount").value(0));

        saveBookmark(saverToken, artwork.id());
        saveBookmark(otherToken, artwork.id());

        // SSR처럼 토큰 없이 불러도 개수는 같다 — 보는 사람과 무관한 값이다.
        mockMvc.perform(get("/api/artworks/{artworkId}", artwork.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarkCount").value(2))
                .andDo(document("artwork/get-detail-bookmark-count",
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("artworkId").description("작품 ID")),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("작품 ID"),
                                fieldWithPath("data.bookmarkCount").description(
                                        "이 작품을 저장한 회원 수 — 보는 사람과 무관하다. 현재 회원의 저장 여부는 "
                                                + "GET /api/bookmarks/{artworkId}")
                        )
                ));

        mockMvc.perform(delete("/api/bookmarks/{artworkId}", artwork.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + saverToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/artworks/{artworkId}", artwork.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarkCount").value(1));
    }

    @Test
    void 저장_여부는_요청한_회원_기준이다() throws Exception {
        ArtworkInfo artwork = publishReady(registerAuthor().id());
        String saverToken = registerMemberAndGetToken("저장회원");
        String otherToken = registerMemberAndGetToken("다른회원");
        saveBookmark(saverToken, artwork.id());

        mockMvc.perform(get("/api/bookmarks/{artworkId}", artwork.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + saverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.bookmarked").value(true))
                .andDo(document("bookmark/get-status",
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("artworkId").description("작품 ID")),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.bookmarked").description(
                                        "현재 회원이 이 작품을 저장했는지 — 저장한 적 없거나 없는 작품이면 false")
                        )
                ));

        mockMvc.perform(get("/api/bookmarks/{artworkId}", artwork.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false));

        mockMvc.perform(delete("/api/bookmarks/{artworkId}", artwork.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + saverToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/bookmarks/{artworkId}", artwork.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + saverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false));
    }

    @Test
    void 저장_여부는_비로그인이면_401() throws Exception {
        ArtworkInfo artwork = publishReady(registerAuthor().id());

        mockMvc.perform(get("/api/bookmarks/{artworkId}", artwork.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andDo(document("bookmark/get-status-unauthenticated", preprocessResponse(prettyPrint())));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    private void saveBookmark(String accessToken, String artworkId) throws Exception {
        mockMvc.perform(post("/api/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("artworkId", artworkId))))
                .andExpect(status().isCreated());
    }

    private ArtworkInfo publishReady(String authorId) {
        ArtworkInfo created = artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                List.of(signedKey(authorId, "bm-status-" + UUID.randomUUID())), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "저장 버튼 문서화 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
        jdbcTemplate.update("UPDATE artworks SET status = 'READY' WHERE id = ?", created.id());
        return created;
    }

    private MemberInfo registerAuthor() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return memberService.register("bm-status-" + suffix + "@atcrew.com", "bmstatus" + suffix, "저장문서작가");
    }

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "bm-status-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR", "KO",
                                "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    /** 그 회원에게 발급된 것과 같은 형태의 업로드 key(#190) — presign을 부르지 않고 같은 규칙으로 만든다. */
    private String signedKey(String memberId, String name) {
        return "raw/" + keySigner.sign(memberId, name) + "/" + name + ".png";
    }

    record RegisterRequest(String email, String password, String passwordConfirm, String name,
                           boolean agreeService, boolean agreePrivacy, boolean agreeThirdParty, boolean agreeMarketing,
                           String timezone, String countryCode, String primaryLanguage,
                           String handle) {    // 사용자 아이디(가입 필수)
    }
}
