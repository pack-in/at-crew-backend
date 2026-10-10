package com.atcrew.artwork;

import java.time.Instant;
import java.util.List;

/**
 * 작품 상세 수정(plans/261005-posthog-analytics-events PA-05) — analytics 모듈이 구독해
 * {@code artwork_edited}로 전송한다.
 *
 * <p>작품은 여러 번 수정될 수 있어 {@code occurredAt}까지 {@code $insert_id}에 넣어 구분한다.
 */
public record ArtworkEditedEvent(
        String artworkId,              // 수정된 작품 ID
        String authorId,                // 수정한 회원 ID — distinct_id로 쓰인다
        List<String> changedSectionIds, // 실제로 값이 바뀐 필드명 목록(섹션ID 체계가 없어 필드명을 그대로 쓴다). 변경 없으면 빈 목록
        Instant occurredAt              // 발생 시각 — $insert_id 구분용
) {
}
