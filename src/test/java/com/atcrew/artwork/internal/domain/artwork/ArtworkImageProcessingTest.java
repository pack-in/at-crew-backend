package com.atcrew.artwork.internal.domain.artwork;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.Visibility;
import com.atcrew.member.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PROCESSING → READY 전환 규칙 회귀 방지 테스트(docs/design/media-module-design.md §5).
 *
 * <p>전환 조건은 "모든 이미지 DONE"이 아니라 "PENDING이 하나도 없고 DONE이 하나 이상"이다 —
 * 전자로 바꾸면 이미지 하나만 FAILED여도 작품이 영원히 READY로 넘어가지 못한다.
 */
class ArtworkImageProcessingTest {

    @Test
    void 모든_이미지가_처리되면_READY로_전환되고_변환결과가_캐시된다() {
        Artwork artwork = artworkWith("raw/1.png", "raw/2.png");

        artwork.markImageProcessed("raw/1.png", "thumb/1.avif", "thumb-adult/1.avif", "original/1.avif", true);
        artwork.markImageProcessed("raw/2.png", "thumb/2.avif", null, "original/2.avif", true);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.READY);
        assertThat(artwork.getImages()).extracting(ArtworkImage::getThumbKey)
                .containsExactly("thumb/1.avif", "thumb/2.avif");
        assertThat(artwork.getImages().get(0).getThumbAdultKey()).isEqualTo("thumb-adult/1.avif");
        assertThat(artwork.getImages()).extracting(ArtworkImage::getOriginalAvifKey)
                .containsExactly("original/1.avif", "original/2.avif");
    }

    @Test
    void 일부_이미지가_실패해도_하나라도_성공했으면_READY로_전환된다() {
        Artwork artwork = artworkWith("raw/1.png", "raw/2.png");

        artwork.markImageProcessed("raw/1.png", "thumb/1.avif", null, "original/1.avif", true);
        artwork.markImageProcessed("raw/2.png", null, null, null, false);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.READY);
        assertThat(artwork.getImages()).extracting(ArtworkImage::getProcessingStatus)
                .containsExactly(ImageProcessingStatus.DONE, ImageProcessingStatus.FAILED);
    }

    @Test
    void 아직_처리중인_이미지가_남아있으면_PROCESSING을_유지한다() {
        Artwork artwork = artworkWith("raw/1.png", "raw/2.png");

        artwork.markImageProcessed("raw/1.png", "thumb/1.avif", null, "original/1.avif", true);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.PROCESSING);
    }

    @Test
    void 모든_이미지가_실패하면_READY로_전환되지_않는다() {
        Artwork artwork = artworkWith("raw/1.png", "raw/2.png");

        artwork.markImageProcessed("raw/1.png", null, null, null, false);
        artwork.markImageProcessed("raw/2.png", null, null, null, false);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.PROCESSING);
    }

    @Test
    void 알_수_없는_이미지_키_콜백은_무시된다() {
        Artwork artwork = artworkWith("raw/1.png");

        artwork.markImageProcessed("raw/없는키.png", "thumb/x.avif", null, "original/x.avif", true);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.PROCESSING);
        assertThat(artwork.getImages().get(0).getProcessingStatus()).isEqualTo(ImageProcessingStatus.PENDING);
    }

    private Artwork artworkWith(String... imageKeys) {
        return Artwork.create("author-1", "제목", "설명", List.of(imageKeys), 0, null,
                ImageLayoutType.VERTICAL_SCROLL, ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), List.of(), AgeRating.ALL, List.of(Language.KO), Visibility.PUBLIC,
                List.of(), null, null, List.of(), List.of());
    }
}
