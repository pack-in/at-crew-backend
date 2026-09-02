package com.atcrew.artwork.internal.application;

import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.common.response.CursorPage;
import com.atcrew.member.Language;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 작가 정보를 찾을 수 없어도 목록 조회가 실패하지 않아야 한다 (이슈 #112).
 *
 * <p>배경: 예전에는 작가마다 {@code memberService.findById(id)}를 부르고 실패하면 {@code catch}로
 * {@code null}을 반환해 넘어가려 했다. 그런데 {@code Collectors.toMap}은 값이 null이면
 * {@link NullPointerException}을 던지므로 **그 방어가 실제로는 동작하지 않았고, 작가 한 명의 조회
 * 실패가 페이지 전체를 500으로 만들었다.** 2026-09-02 부하 측정 중 회원 하나의 enum 값이 어긋나
 * 실제로 재현됐다.
 *
 * <p>지금은 {@code MemberService.findAllByIds}가 없는 ID를 결과에서 빼고, 호출부는 맵 조회
 * 결과가 null인 것으로 판단한다. 이 테스트는 그 계약을 고정한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ImportTestcontainers(SharedContainersConfig.class)
class ArtworkAuthorLookupTest {

    private static final String MISSING_AUTHOR_ID = "does-not-exist-author";

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    ArtworkRepository artworkRepository;

    @BeforeEach
    void 존재하지_않는_작가의_작품을_저장한다() {
        artworkRepository.deleteAll();
        Artwork artwork = Artwork.create(
                MISSING_AUTHOR_ID, "작가 없는 작품", "설명",
                List.of("raw/1.png"), 0, null,
                ImageLayoutType.VERTICAL_SCROLL, ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.values()[0]), List.of(Genre.values()[0]), null,
                List.of("태그"), AgeRating.ALL, List.of(Language.KO), Visibility.PUBLIC,
                List.of("도구"), null, null, List.of(), List.of());
        artwork.markImageProcessed("raw/1.png", "thumb/1.avif", null, "original/1.avif", true);
        artworkRepository.save(artwork);
    }

    @Test
    void 작가를_찾을_수_없어도_커뮤니티_목록이_500이_되지_않는다() {
        assertThatCode(() -> {
            CursorPage<ArtworkSummaryInfo> page = artworkService.getCommunityArtworks(
                    null, null, null, null, null, 20, null, false);
            // 작가 정보만 비고 작품 자체는 목록에 남아야 한다.
            assertThat(page.items()).isNotEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void 배치_조회는_없는_ID를_결과에서_뺀다() {
        Map<String, MemberInfo> found = memberService.findAllByIds(Set.of(MISSING_AUTHOR_ID));

        assertThat(found).isEmpty();
    }

    @Test
    void 배치_조회는_빈_입력에_질의하지_않고_빈_맵을_돌려준다() {
        assertThat(memberService.findAllByIds(Set.of())).isEmpty();
        assertThat(memberService.findAllByIds(null)).isEmpty();
    }
}
