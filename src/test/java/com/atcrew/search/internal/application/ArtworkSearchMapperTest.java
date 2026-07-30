package com.atcrew.search.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkImageInfo;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.MaterialInfo;
import com.atcrew.artwork.Visibility;
import com.atcrew.search.internal.domain.ArtworkSearchDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtworkSearchMapperTest {

    @Test
    void 기본_필드가_그대로_변환된다() {
        ArtworkInfo info = artworkInfo(null, 0, List.of());

        ArtworkSearchDocument doc = ArtworkSearchMapper.toDocument(info);

        assertThat(doc.getId()).isEqualTo("artwork-1");
        assertThat(doc.getTitle()).isEqualTo("제목");
        assertThat(doc.getDescription()).isEqualTo("설명");
        assertThat(doc.getTags()).containsExactly("태그1", "태그2");
        assertThat(doc.getAuthorId()).isEqualTo("author-1");
        assertThat(doc.getAuthorName()).isEqualTo("작가이름");
        assertThat(doc.getAuthorHandle()).isEqualTo("handle1");
        assertThat(doc.getArtworkField()).isEqualTo(ArtworkField.ILLUSTRATION.name());
        assertThat(doc.getCreativeType()).isEqualTo(CreativeType.ORIGINAL.name());
        assertThat(doc.getAgeRating()).isEqualTo(AgeRating.ALL.name());
        assertThat(doc.getRoles()).containsExactly(ArtworkRole.LINEART.name());
        assertThat(doc.getGenres()).containsExactly("BL");
    }

    @Test
    void 소재_대상이_여러_material에서_평탄화되고_중복이_제거된다() {
        List<MaterialInfo> materials = List.of(
                new MaterialInfo("소재1", List.of("무기", "배경"), List.of(), List.of()),
                new MaterialInfo("소재2", List.of("배경", "인물"), List.of(), List.of())
        );
        ArtworkInfo info = artworkInfo(null, 0, materials);

        ArtworkSearchDocument doc = ArtworkSearchMapper.toDocument(info);

        assertThat(doc.getMaterialTargets()).containsExactlyInAnyOrder("무기", "배경", "인물");
    }

    @Test
    void 사용자_지정_썸네일이_있으면_그것을_우선_사용하고_성인용_썸네일은_null이다() {
        ArtworkInfo info = artworkInfo("custom-thumb-key", 0, List.of());

        ArtworkSearchDocument doc = ArtworkSearchMapper.toDocument(info);

        assertThat(doc.getThumbnailKey()).isEqualTo("custom-thumb-key");
        assertThat(doc.getThumbnailAdultKey()).isNull();
    }

    @Test
    void 사용자_지정_썸네일이_없으면_대표_이미지의_처리된_썸네일을_사용한다() {
        ArtworkInfo info = artworkInfo(null, 1, List.of()); // 대표 이미지 인덱스 1

        ArtworkSearchDocument doc = ArtworkSearchMapper.toDocument(info);

        assertThat(doc.getThumbnailKey()).isEqualTo("rep-thumb-1");
        assertThat(doc.getThumbnailAdultKey()).isEqualTo("rep-thumb-adult-1");
    }

    private ArtworkInfo artworkInfo(String thumbnailKey, int representativeImageIndex, List<MaterialInfo> materials) {
        List<ArtworkImageInfo> images = List.of(
                new ArtworkImageInfo("orig-0", "rep-thumb-0", "rep-thumb-adult-0", "orig-0.avif", ImageProcessingStatus.DONE),
                new ArtworkImageInfo("orig-1", "rep-thumb-1", "rep-thumb-adult-1", "orig-1.avif", ImageProcessingStatus.DONE)
        );
        return new ArtworkInfo(
                "artwork-1", "author-1", "작가이름", "handle1",
                "제목", "설명", images, representativeImageIndex, thumbnailKey, ImageLayoutType.VERTICAL_SCROLL,
                ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL, List.of(ArtworkRole.LINEART),
                List.of("BL"), List.of("태그1", "태그2"), List.of(), null, null, List.of(),
                AgeRating.ALL, Visibility.PUBLIC, materials, ArtworkStatus.READY,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z")
        );
    }
}
