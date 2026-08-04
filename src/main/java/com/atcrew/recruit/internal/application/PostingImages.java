package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.internal.domain.RecruitImageRole;
import com.atcrew.recruit.internal.domain.RecruitPostingImage;

import java.util.Comparator;
import java.util.List;

/**
 * 응답 DTO의 {@code thumbnailImage}/{@code referenceImages}에 실을 값 (설계 §10.4).
 *
 * <p>자식 테이블 행에서 조립하며, 각 행은 변환이 끝났으면 {@code originalAvifKey}, 아직 처리 중이면
 * {@code originalKey}로 폴백한다. DTO의 필드 이름·타입은 그대로이므로 API 브레이킹 체인지가 아니다.
 */
record PostingImages(String thumbnailImage, List<String> referenceImages) {

    static PostingImages of(List<? extends RecruitPostingImage> images) {
        String thumbnail = images.stream()
                .filter(i -> i.getRole() == RecruitImageRole.THUMBNAIL)
                .min(Comparator.comparingInt(RecruitPostingImage::getOrdinal))
                .map(RecruitPostingImage::displayKey)
                .orElse(null);
        List<String> references = images.stream()
                .filter(i -> i.getRole() == RecruitImageRole.REFERENCE)
                .sorted(Comparator.comparingInt(RecruitPostingImage::getOrdinal))
                .map(RecruitPostingImage::displayKey)
                .toList();
        return new PostingImages(thumbnail, references);
    }

    /**
     * 자식 행이 없는 게시글(이 변경 이전에 저장된 데이터)은 기존 컬럼 값을 그대로 쓴다.
     */
    static PostingImages legacy(String thumbnailImage, List<String> referenceImages) {
        return new PostingImages(thumbnailImage, referenceImages != null ? referenceImages : List.of());
    }
}
