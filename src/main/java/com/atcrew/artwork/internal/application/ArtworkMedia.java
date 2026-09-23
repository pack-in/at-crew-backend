package com.atcrew.artwork.internal.application;

import com.atcrew.media.MediaAssetInfo;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 작품 한 건의 media 자산 — 본문 이미지와 사용자 지정 썸네일은 owner가 달라({@link MediaOwnerType#ARTWORK},
 * {@link MediaOwnerType#ARTWORK_THUMBNAIL}) 응답을 만들 때마다 둘을 함께 읽는다.
 *
 * @param images    본문 이미지(ordinal 순)
 * @param thumbnail 사용자 지정 썸네일 자산. 썸네일을 변환하기 전에 올라온 작품이거나 지정 썸네일이 없으면 null
 */
record ArtworkMedia(List<MediaAssetInfo> images, MediaAssetInfo thumbnail) {

    static ArtworkMedia load(MediaService mediaService, String artworkId) {
        List<MediaAssetInfo> thumbnails = mediaService.getAssets(MediaOwnerType.ARTWORK_THUMBNAIL, artworkId);
        return new ArtworkMedia(mediaService.getAssets(MediaOwnerType.ARTWORK, artworkId),
                thumbnails.isEmpty() ? null : thumbnails.get(0));
    }

    /** 목록용 일괄 조회 — 소유자 종류마다 한 번씩 읽는다. 자산이 없는 작품도 빈 값으로 담긴다. */
    static Map<String, ArtworkMedia> loadAll(MediaService mediaService, Collection<String> artworkIds) {
        Map<String, List<MediaAssetInfo>> images = mediaService.getAssets(MediaOwnerType.ARTWORK, artworkIds);
        Map<String, List<MediaAssetInfo>> thumbnails = mediaService.getAssets(MediaOwnerType.ARTWORK_THUMBNAIL, artworkIds);
        return artworkIds.stream().distinct().collect(Collectors.toMap(Function.identity(), id -> {
            List<MediaAssetInfo> thumbnail = thumbnails.getOrDefault(id, List.of());
            return new ArtworkMedia(images.getOrDefault(id, List.of()), thumbnail.isEmpty() ? null : thumbnail.get(0));
        }));
    }
}
