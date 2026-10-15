package com.atcrew.artwork;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArtworkOgImageTest {

    @Test
    void 전체연령가는_일반_og를_쓴다() {
        assertThat(ArtworkOgImage.select(AgeRating.ALL, "og/a.jpg", "og-adult/a.jpg")).isEqualTo("og/a.jpg");
    }

    @Test
    void 성인물은_블러_og를_쓴다() {
        assertThat(ArtworkOgImage.select(AgeRating.R18, "og/a.jpg", "og-adult/a.jpg")).isEqualTo("og-adult/a.jpg");
        assertThat(ArtworkOgImage.select(AgeRating.G18, "og/a.jpg", "og-adult/a.jpg")).isEqualTo("og-adult/a.jpg");
    }

    // 블러 전 원본은 og:image로 내보내지 않는다(기획서) — 블러본이 없으면 일반 og로 대체하지 않고 기본 이미지로 보낸다.
    @Test
    void 성인물인데_블러_og가_없으면_일반_og로_대체하지_않는다() {
        assertThat(ArtworkOgImage.select(AgeRating.R18, "og/a.jpg", null)).isNull();
    }

    @Test
    void og가_없으면_null이다() {
        assertThat(ArtworkOgImage.select(AgeRating.ALL, null, null)).isNull();
        assertThat(ArtworkOgImage.select(null, "og/a.jpg", "og-adult/a.jpg")).isNull();
    }
}
