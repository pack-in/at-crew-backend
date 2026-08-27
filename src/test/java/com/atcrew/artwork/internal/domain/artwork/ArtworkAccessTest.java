package com.atcrew.artwork.internal.domain.artwork;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkAccess;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.Visibility;
import com.atcrew.member.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제3자 접근 판정 회귀 방지 테스트 (마이페이지_작가-R04).
 *
 * <p>공개 여부는 "피드 공개 여부 × 라이브 포트폴리오 편입 여부" 2요소 파생 상태이며 "링크 공개"라는
 * 제3의 상태를 인정하지 않는다 — 레거시 {@code LINK_ONLY}는 {@code PRIVATE}와 동일하게 취급한다.
 */
@SuppressWarnings("deprecation") // 라이트 ETL 매핑용 레거시 값의 판정 회귀를 검증하는 테스트다.
class ArtworkAccessTest {

    private static final String VIEWER_ID = "viewer-1";

    @Test
    void 링크공개_작품이_라이브_포트폴리오에_없으면_제3자_접근을_막는다() {
        Artwork artwork = readyArtwork(Visibility.LINK_ONLY);

        assertThat(artwork.accessFor(VIEWER_ID)).isEqualTo(ArtworkAccess.PRIVATE);
    }

    @Test
    void 링크공개_작품도_라이브_포트폴리오에_편입되면_제3자_접근을_허용한다() {
        Artwork artwork = readyArtwork(Visibility.LINK_ONLY);

        artwork.updatePortfolioInclusion(true);

        assertThat(artwork.accessFor(VIEWER_ID)).isEqualTo(ArtworkAccess.ALLOWED);
    }

    @Test
    void 비공개_작품도_라이브_포트폴리오에_편입되면_제3자_접근을_허용한다() {
        Artwork artwork = readyArtwork(Visibility.PRIVATE);

        artwork.updatePortfolioInclusion(true);

        assertThat(artwork.accessFor(VIEWER_ID)).isEqualTo(ArtworkAccess.ALLOWED);
    }

    @Test
    void 피드_공개_작품은_편입_여부와_무관하게_제3자_접근을_허용한다() {
        Artwork artwork = readyArtwork(Visibility.PUBLIC);

        assertThat(artwork.accessFor(VIEWER_ID)).isEqualTo(ArtworkAccess.ALLOWED);
    }

    @Test
    void 완전_비공개_작품도_작성자_본인은_열람한다() {
        Artwork artwork = readyArtwork(Visibility.PRIVATE);

        assertThat(artwork.accessFor("author-1")).isEqualTo(ArtworkAccess.ALLOWED);
    }

    // 제3자 판정은 READY 상태에서만 공개 여부를 따지므로 이미지 처리 완료까지 태워 둔다.
    private Artwork readyArtwork(Visibility visibility) {
        Artwork artwork = Artwork.create("author-1", "제목", "설명", List.of("raw/1.png"), 0, null,
                ImageLayoutType.VERTICAL_SCROLL, ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), List.of(), AgeRating.ALL, List.of(Language.KO), visibility,
                List.of(), null, null, List.of(), List.of());
        artwork.markImageProcessed("raw/1.png", "thumb/1.avif", null, "original/1.avif", true);
        return artwork;
    }
}
