package com.atcrew.recruit.internal.application;

import com.atcrew.media.MediaAssetInfo;
import com.atcrew.recruit.internal.domain.RecruitImageRole;

import java.util.Comparator;
import java.util.List;

/**
 * 응답 DTO의 {@code thumbnailImage}/{@code referenceImages}에 실을 값 (설계 §10.4).
 *
 * <p>media 자산에서 조립하며, 각 자산은 변환이 끝났으면 {@code originalAvifKey}, 아직 처리 중이면
 * {@code originalKey}로 폴백한다. DTO의 필드 이름·타입은 그대로이므로 API 브레이킹 체인지가 아니다.
 */
record PostingImages(String thumbnailImage, List<String> referenceImages) {

    static PostingImages of(List<MediaAssetInfo> assets) {
        String thumbnail = assets.stream()
                .filter(a -> RecruitImageRole.THUMBNAIL.name().equals(a.slotRole()))
                .min(Comparator.comparingInt(MediaAssetInfo::ordinal))
                .map(PostingImages::displayKey)
                .orElse(null);
        List<String> references = assets.stream()
                .filter(a -> RecruitImageRole.REFERENCE.name().equals(a.slotRole()))
                .sorted(Comparator.comparingInt(MediaAssetInfo::ordinal))
                .map(PostingImages::displayKey)
                .toList();
        return new PostingImages(thumbnail, references);
    }

    /**
     * 자산이 없는 게시글(이 변경 이전에 저장된 데이터)은 기존 컬럼 값을 그대로 쓴다.
     */
    static PostingImages legacy(String thumbnailImage, List<String> referenceImages) {
        return new PostingImages(thumbnailImage, referenceImages != null ? referenceImages : List.of());
    }

    /** 변환이 끝났으면 AVIF를, 아직이면 원본 key를 쓴다 — 처리 중에도 화면에 무언가는 보여야 한다. */
    private static String displayKey(MediaAssetInfo asset) {
        return asset.originalAvifKey() != null ? asset.originalAvifKey() : asset.originalKey();
    }
}
