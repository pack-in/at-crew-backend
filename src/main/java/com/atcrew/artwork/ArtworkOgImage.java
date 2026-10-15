package com.atcrew.artwork;

/**
 * 공유 링크 미리보기(OG)에 내보낼 이미지 key를 고르는 규칙 — 작품 상세·포트폴리오 공유 헤더·스냅샷 백필이 모두 이 규칙을 쓴다
 * (docs/design/og-image-design.md).
 *
 * <p>성인물(R18·G18)은 블러본만 내보낸다. 블러본이 없으면 일반 OG로 대체하지 않고 null을 돌려준다 — 기획서가 "blur 전
 * 원본 썸네일은 og:image로 제공하지 않는다"고 정했고, null이면 FE가 서비스 기본 이미지를 쓴다. 판정을 FE에 맡기지 않는
 * 것도 같은 이유다(판정이 틀리면 블러 안 된 성인물이 외부에 나간다).
 */
public final class ArtworkOgImage {

    private ArtworkOgImage() {
    }

    /** 등급을 모르면(null) 성인물일 수 있으므로 내보내지 않는다. */
    public static String select(AgeRating ageRating, String ogKey, String ogAdultKey) {
        if (ageRating == null) {
            return null;
        }
        return ageRating == AgeRating.ALL ? ogKey : ogAdultKey;
    }
}
