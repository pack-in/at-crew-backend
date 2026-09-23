package com.atcrew.artwork;

import java.util.List;

/**
 * 작품 카드에 띄울 썸네일 key 한 쌍 — 작품 목록·검색·포트폴리오 카드가 모두 이 규칙을 쓴다.
 *
 * <p>예전에는 세 모듈이 같은 판정을 각자 복제했다. 썸네일이 media 자산이 되면서 판정이 한 단계 늘어 한 곳에 모은다.
 *
 * @param thumbKey      카드 썸네일 R2 key
 * @param thumbAdultKey 성인물 블러 썸네일 R2 key(홈-R06). 블러본이 없으면 null
 */
public record ArtworkCardThumbnail(String thumbKey, String thumbAdultKey) {

    public static ArtworkCardThumbnail of(ArtworkInfo artwork) {
        return of(artwork.thumbnailImage(), artwork.thumbnailKey(), artwork.images(),
                artwork.representativeImageIndex());
    }

    /**
     * 판정 순서:
     * <ol>
     *   <li>사용자 지정 썸네일 자산 — 변환이 끝났으면 {@code thumbKey}, 아직이거나 실패했으면 raw 원본(Worker는 변환에
     *       성공한 뒤에만 raw를 지운다).</li>
     *   <li>자산이 없는 지정 썸네일 — 썸네일을 변환하기 전에 올라온 작품이라 raw를 그대로 쓴다. 블러본은 없다.</li>
     *   <li>지정 썸네일이 없는 작품 — 대표 이미지의 예전 썸네일, 그것도 없으면 대표 이미지의 표시 key.</li>
     * </ol>
     */
    public static ArtworkCardThumbnail of(ArtworkImageInfo thumbnailImage, String thumbnailKey,
                                          List<ArtworkImageInfo> images, int representativeImageIndex) {
        if (thumbnailImage != null) {
            return new ArtworkCardThumbnail(
                    thumbnailImage.thumbKey() != null ? thumbnailImage.thumbKey() : thumbnailImage.originalKey(),
                    thumbnailImage.thumbAdultKey());
        }
        if (thumbnailKey != null) {
            return new ArtworkCardThumbnail(thumbnailKey, null);
        }
        if (images == null || images.isEmpty()) {
            return new ArtworkCardThumbnail(null, null);
        }
        // 목록이 줄어 인덱스가 벗어나면 마지막 이미지로 자른다(작품 목록의 기존 동작).
        ArtworkImageInfo representative = images.get(Math.min(Math.max(representativeImageIndex, 0), images.size() - 1));
        if (representative.thumbKey() != null) {
            return new ArtworkCardThumbnail(representative.thumbKey(), representative.thumbAdultKey());
        }
        return new ArtworkCardThumbnail(representative.originalAvifKey() != null
                ? representative.originalAvifKey() : representative.originalKey(), null);
    }
}
