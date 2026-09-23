package com.atcrew.artwork;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtworkCardThumbnailTest {

    private static final List<ArtworkImageInfo> LEGACY_IMAGES = List.of(
            image("raw/0.png", "thumb/0.avif", "thumb-adult/0.avif", "original/0.avif"),
            image("raw/1.png", "thumb/1.avif", "thumb-adult/1.avif", "original/1.avif"));

    @Test
    void 변환된_지정_썸네일은_썸네일과_블러본을_쓴다() {
        ArtworkImageInfo thumbnail = image("raw/t.png", "thumb/t.avif", "thumb-adult/t.avif", null);

        assertThat(ArtworkCardThumbnail.of(thumbnail, "raw/t.png", LEGACY_IMAGES, 0))
                .isEqualTo(new ArtworkCardThumbnail("thumb/t.avif", "thumb-adult/t.avif"));
    }

    // Worker는 변환에 성공한 뒤에만 raw를 지우므로 처리 전·실패 상태에서는 raw가 남아 있다.
    @Test
    void 변환_전이거나_실패한_지정_썸네일은_raw를_쓴다() {
        ArtworkImageInfo pending = image("raw/t.png", null, null, null);

        assertThat(ArtworkCardThumbnail.of(pending, "raw/t.png", LEGACY_IMAGES, 0))
                .isEqualTo(new ArtworkCardThumbnail("raw/t.png", null));
    }

    @Test
    void 자산_없는_옛_지정_썸네일은_raw를_쓰고_블러본은_없다() {
        assertThat(ArtworkCardThumbnail.of(null, "raw/legacy.png", LEGACY_IMAGES, 1))
                .isEqualTo(new ArtworkCardThumbnail("raw/legacy.png", null));
    }

    @Test
    void 지정_썸네일이_없으면_대표_이미지의_옛_썸네일을_쓴다() {
        assertThat(ArtworkCardThumbnail.of(null, null, LEGACY_IMAGES, 1))
                .isEqualTo(new ArtworkCardThumbnail("thumb/1.avif", "thumb-adult/1.avif"));
    }

    // 역할 분리 이후 본문 이미지에는 thumb가 없다 — 지정 썸네일 없이 올라온 작품도 카드가 비지 않게 한다.
    @Test
    void 대표_이미지에_썸네일이_없으면_본문_변환본을_쓴다() {
        List<ArtworkImageInfo> images = List.of(image("raw/0.png", null, null, "original/0.avif"));

        assertThat(ArtworkCardThumbnail.of(null, null, images, 0))
                .isEqualTo(new ArtworkCardThumbnail("original/0.avif", null));
    }

    @Test
    void 대표_인덱스가_목록을_벗어나면_마지막_이미지를_쓰고_이미지가_없으면_비운다() {
        assertThat(ArtworkCardThumbnail.of(null, null, LEGACY_IMAGES, 5).thumbKey()).isEqualTo("thumb/1.avif");
        assertThat(ArtworkCardThumbnail.of(null, null, List.of(), 0))
                .isEqualTo(new ArtworkCardThumbnail(null, null));
    }

    private static ArtworkImageInfo image(String originalKey, String thumbKey, String thumbAdultKey,
                                          String originalAvifKey) {
        return new ArtworkImageInfo(originalKey, thumbKey, thumbAdultKey, originalAvifKey,
                thumbKey != null || originalAvifKey != null ? ImageProcessingStatus.DONE : ImageProcessingStatus.PENDING);
    }
}
