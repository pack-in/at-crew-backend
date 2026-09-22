package com.atcrew.artwork.docs;

import com.atcrew.media.internal.application.MediaKeySigner;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.common.security.JwtProvider;
import com.atcrew.member.Language;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 작품 열람 기록 API(POST /api/artworks/{artworkId}/views) 문서화 통합 테스트 — 홈-R14.
 *
 * <p>24시간 dedup·동시성·탈퇴 비식별화 같은 판정 규칙은 {@code ArtworkViewRecordTests}가 검증한다.
 * 여기서는 HTTP 계약(인증 선택, 헤더, 항상 204, 형식 오류 400)과 GET 상세가 조회수를 올리지 않는 것을 남긴다.
 */
class ArtworkViewApiDocTest extends RestDocsIntegrationSupport {

    private static final String ANONYMOUS_ID_HEADER = "X-Anonymous-Id";

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 비로그인_열람_기록_문서화() throws Exception {
        String artworkId = publishReady(registerMember().id());

        mockMvc.perform(post("/api/artworks/{artworkId}/views", artworkId)
                        .header(ANONYMOUS_ID_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isNoContent())
                .andDo(document("artwork/record-view",
                        pathParameters(
                                parameterWithName("artworkId").description("작품 ID")
                        ),
                        requestHeaders(
                                headerWithName(ANONYMOUS_ID_HEADER).description(
                                        "비로그인 열람자 익명 UUID(FE 1st-party 쿠키 값, 표준 36자 표기). "
                                                + "로그인 요청이면 무시된다").optional(),
                                headerWithName(HttpHeaders.AUTHORIZATION).description(
                                        "Bearer 토큰 (선택). 있으면 회원 기준으로 기록한다").optional()
                        )));

        assertThat(viewCountOf(artworkId)).isEqualTo(1L);
    }

    @Test
    void 로그인_회원은_익명_헤더가_있어도_회원_기준으로_기록한다() throws Exception {
        String artworkId = publishReady(registerMember().id());
        MemberInfo viewer = registerMember();

        mockMvc.perform(post("/api/artworks/{artworkId}/views", artworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf(viewer))
                        .header(ANONYMOUS_ID_HEADER, "broken-cookie-value"))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT viewer_key FROM artwork_view_events WHERE artwork_id = ? AND viewer_type = 'MEMBER'",
                String.class, artworkId)).isEqualTo(viewer.id());
    }

    @Test
    void 기록하지_않는_열람도_204로_응답한다() throws Exception {
        String authorId = registerMember().id();
        String artworkId = publishReady(authorId);

        // 식별값 없음, 없는 작품 — 응답만으로는 기록 여부를 알 수 없어야 한다.
        mockMvc.perform(post("/api/artworks/{artworkId}/views", artworkId))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/artworks/{artworkId}/views", UUID.randomUUID().toString())
                        .header(ANONYMOUS_ID_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());

        assertThat(viewCountOf(artworkId)).isZero();
    }

    @Test
    void 익명_ID가_UUID_형식이_아니면_400_문서화() throws Exception {
        String artworkId = publishReady(registerMember().id());

        mockMvc.perform(post("/api/artworks/{artworkId}/views", artworkId)
                        .header(ANONYMOUS_ID_HEADER, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ANONYMOUS_ID"))
                .andDo(document("artwork/record-view-invalid-anonymous-id", preprocessResponse(prettyPrint())));
    }

    @Test
    void 작품_상세_조회는_조회수를_올리지_않는다() throws Exception {
        String artworkId = publishReady(registerMember().id());

        mockMvc.perform(get("/api/artworks/{artworkId}", artworkId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/artworks/{artworkId}", artworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf(registerMember())))
                .andExpect(status().isOk());

        assertThat(viewCountOf(artworkId)).isZero();
    }

    private long viewCountOf(String artworkId) {
        return jdbcTemplate.queryForObject("SELECT view_count FROM artworks WHERE id = ?", Long.class, artworkId);
    }

    private String tokenOf(MemberInfo member) {
        return jwtProvider.generateAccessToken(member.id(), member.loginEmail());
    }

    private String publishReady(String authorId) {
        String artworkId = artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                List.of(signedKey(authorId, "view-doc-" + UUID.randomUUID())), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "열람 문서화 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of())).id();
        jdbcTemplate.update("UPDATE artworks SET status = 'READY' WHERE id = ?", artworkId);
        return artworkId;
    }

    private MemberInfo registerMember() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return memberService.register("view-doc-" + suffix + "@atcrew.com", "viewdoc" + suffix, "열람문서");
    }

    /**
     * 그 회원에게 발급된 것과 같은 형태의 업로드 key(#190) — 소유 검증이 서명만 보므로 presign을 부르지 않고
     * 같은 규칙으로 만든다.
     */
    private String signedKey(String memberId, String name) {
        return "raw/" + keySigner.sign(memberId, name) + "/" + name + ".png";
    }

}
