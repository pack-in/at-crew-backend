package com.atcrew.artwork.docs;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 작품 공개 주소 조회 API(GET /api/members/{handle}/artworks/{code}) 문서화 통합 테스트
 * (docs/design/member-handle-design.md §2.2). 접근 판정은 기존 ArtworkAccess를 그대로 쓰므로 여기서는
 * HTTP 계약(옛 handle 수용, 현재 handle 응답, 404)만 남긴다.
 */
class ArtworkPublicAddressApiDocTest extends RestDocsIntegrationSupport {

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
    void 작품_공개_주소_조회_문서화() throws Exception {
        MemberInfo author = registerMember();
        var artwork = publishReady(author.id());

        mockMvc.perform(get("/api/members/{handle}/artworks/{code}", author.handle(), artwork.publicCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(artwork.id()))
                .andExpect(jsonPath("$.data.authorHandle").value(author.handle()))
                .andExpect(jsonPath("$.data.publicCode").value(artwork.publicCode()))
                .andDo(document("artwork/get-by-public-code",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("handle").description("작가 사용자 아이디 — 옛 아이디도 받는다(대소문자 무관)"),
                                parameterWithName("code").description("작품 공개 코드 (영문 소문자·숫자 6자)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("작품 ID — 수정·삭제·열람 기록(POST /views) 등 내부 API에 쓴다"),
                                fieldWithPath("data.authorId").description("작가 회원 ID"),
                                fieldWithPath("data.authorName").description("작가 이름"),
                                fieldWithPath("data.authorHandle").description(
                                        "작가의 현재 사용자 아이디 — URL의 handle과 대소문자 무시 비교로 다르면 이 값으로 308 리다이렉트"),
                                fieldWithPath("data.publicCode").description(
                                        "작품 공개 코드(6자) — 공개 URL /{authorHandle}/artwork/{publicCode}"),
                                fieldWithPath("data.title").description("작품 제목"),
                                fieldWithPath("data.visibility").description("공개 범위"),
                                fieldWithPath("data.status").description("이미지 처리 상태")
                        )
                ));

        mockMvc.perform(get("/api/members/{handle}/artworks/{code}", author.handle(), "zzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTWORK_NOT_FOUND"))
                .andDo(document("artwork/get-by-public-code-not-found", preprocessResponse(prettyPrint())));

        mockMvc.perform(get("/api/members/{handle}/artworks/{code}", "nobody_never_used", artwork.publicCode()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andDo(document("artwork/get-by-public-code-member-not-found", preprocessResponse(prettyPrint())));
    }

    private com.atcrew.artwork.ArtworkInfo publishReady(String authorId) {
        com.atcrew.artwork.ArtworkInfo created = artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                List.of(signedKey(authorId, "addr-doc-" + UUID.randomUUID())), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "공개 주소 문서화 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
        jdbcTemplate.update("UPDATE artworks SET status = 'READY' WHERE id = ?", created.id());
        return created;
    }

    private MemberInfo registerMember() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return memberService.register("addr-doc-" + suffix + "@atcrew.com", "addrdoc" + suffix, "주소문서");
    }

    /** 그 회원에게 발급된 것과 같은 형태의 업로드 key(#190) — presign을 부르지 않고 같은 규칙으로 만든다. */
    private String signedKey(String memberId, String name) {
        return "raw/" + keySigner.sign(memberId, name) + "/" + name + ".png";
    }
}
