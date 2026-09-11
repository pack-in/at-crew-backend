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
 *
 * <p>PENDING이 없는데 DONE도 없는 경우(전량 실패)는 READY가 아니라 FAILED로 끝낸다 — 이 분기가 없으면
 * 작품이 PROCESSING에 영구 고착되고, 재시도 스케줄러는 PENDING만 다루므로 자력 복구도 불가능하다.
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
    void 모든_이미지가_실패하면_FAILED로_전환된다() {
        Artwork artwork = artworkWith("raw/1.png", "raw/2.png");

        artwork.markImageProcessed("raw/1.png", null, null, null, false);
        artwork.markImageProcessed("raw/2.png", null, null, null, false);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.FAILED);
    }

    // 업로드 직후 PROCESSING 상태에서 휴지통에 넣으면 Worker 콜백이 그 뒤에 도착한다 — 이때 상태를
    // 덮어쓰면 사용자가 버린 작품이 휴지통에서 사라지고 되살아난다.
    @Test
    void 휴지통에_있는_작품은_늦게_도착한_콜백으로_되살아나지_않는다() {
        Artwork artwork = artworkWith("raw/1.png");
        artwork.moveToTrash();

        artwork.markImageProcessed("raw/1.png", "thumb/1.avif", null, "original/1.avif", true);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.DELETED);
        // 이미지 변환 결과 자체는 반영해 둔다 — 복구했을 때 그대로 쓸 수 있어야 한다.
        assertThat(artwork.getImages().get(0).getProcessingStatus()).isEqualTo(ImageProcessingStatus.DONE);
        assertThat(artwork.getImages().get(0).getThumbKey()).isEqualTo("thumb/1.avif");
    }

    @Test
    void 휴지통에_있는_작품은_전량_실패_콜백으로도_상태가_바뀌지_않는다() {
        Artwork artwork = artworkWith("raw/1.png");
        artwork.moveToTrash();

        artwork.markImageProcessed("raw/1.png", null, null, null, false);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.DELETED);
        assertThat(artwork.getImages().get(0).getProcessingStatus()).isEqualTo(ImageProcessingStatus.FAILED);
    }

    // 휴지통 복구는 삭제 전 상태를 기억하는 대신 이미지 현황으로 다시 계산한다(이슈 #146) —
    // 예전에는 무조건 READY라 이미지가 없거나 전량 실패한 작품이 공개 상태로 살아났다.
    @Test
    void 처리_중에_버린_작품을_복구하면_PROCESSING으로_돌아온다() {
        Artwork artwork = artworkWith("raw/1.png", "raw/2.png");
        artwork.markImageProcessed("raw/1.png", "thumb/1.avif", null, "original/1.avif", true);
        artwork.moveToTrash();

        artwork.restore();

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.PROCESSING);
    }

    @Test
    void 전량_실패한_작품을_복구하면_FAILED로_돌아온다() {
        Artwork artwork = artworkWith("raw/1.png");
        artwork.markImageProcessed("raw/1.png", null, null, null, false);
        artwork.moveToTrash();

        artwork.restore();

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.FAILED);
    }

    @Test
    void 정상_작품을_복구하면_READY로_돌아온다() {
        Artwork artwork = artworkWith("raw/1.png");
        artwork.markImageProcessed("raw/1.png", "thumb/1.avif", null, "original/1.avif", true);
        artwork.moveToTrash();

        artwork.restore();

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.READY);
    }

    // 삭제 시점 상태를 스냅샷으로 저장하는 방식이었다면 PROCESSING으로 되살아났을 경우다 —
    // 휴지통에 있는 동안에도 콜백은 이미지 행을 계속 갱신하므로 스냅샷은 낡은 값이 된다.
    @Test
    void 버린_뒤_처리가_끝난_작품을_복구하면_READY로_돌아온다() {
        Artwork artwork = artworkWith("raw/1.png");
        artwork.moveToTrash();
        artwork.markImageProcessed("raw/1.png", "thumb/1.avif", null, "original/1.avif", true);

        artwork.restore();

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.READY);
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
                List.of(), List.of(), null, List.of(), AgeRating.ALL, List.of(Language.KO), Visibility.PUBLIC,
                List.of(), null, null, List.of(), List.of());
    }
}
