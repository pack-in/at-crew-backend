package com.atcrew.artwork;

/**
 * 작품 업로드 성공(plans/261005-posthog-analytics-events PA-05) — analytics 모듈이 구독해
 * {@code artwork_published}로 전송한다.
 *
 * <p>작품은 한 번만 업로드되므로({@code artworkId}가 새로 생성됨) 재발행돼도 같은 사건을 가리킨다 —
 * {@code $insert_id}는 타임스탬프 없이 {@code artworkId}만으로 특정한다.
 */
public record ArtworkPublishedEvent(
        String artworkId,        // 업로드된 작품 ID
        String authorId,         // 업로드한 회원 ID — distinct_id로 쓰인다
        long uploadOrder,        // 이 작품을 포함해 이 회원이 보유한(휴지통 제외) 작품 수
        boolean isFirstArtwork,  // uploadOrder == 1
        Visibility visibility    // 업로드 시점의 피드 공개 여부
) {
}
