package com.atcrew.artwork;

import java.time.Instant;

/**
 * 작품 휴지통 이동(plans/261005-posthog-analytics-events PA-05) — analytics 모듈이 구독해
 * {@code artwork_deleted}로 전송한다.
 *
 * <p>복구 후 재삭제로 같은 작품이 여러 번 삭제될 수 있어 {@code occurredAt}까지 {@code $insert_id}에
 * 넣어 구분한다. 삭제 당시의 업로드 순번·편입 포트폴리오 수는 artwork 모듈만으로 계산할 수 없어 싣지 않는다.
 */
public record ArtworkDeletedEvent(
        String artworkId,  // 삭제(휴지통 이동)된 작품 ID
        String authorId,   // 삭제한 회원 ID — distinct_id로 쓰인다
        Instant occurredAt // 발생 시각 — $insert_id 구분용
) {
}
