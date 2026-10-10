package com.atcrew.artwork;

import java.time.Instant;

/**
 * 북마크 저장(plans/261005-posthog-analytics-events PA-05) — analytics 모듈이 구독해
 * {@code bookmark_saved}로 전송한다.
 *
 * <p>현재 북마크 대상은 작품뿐이라 {@code contentType}은 리스너가 "artwork" 고정값으로 싣는다.
 * 같은 회원이 저장·해제를 반복할 수 있어 {@code occurredAt}까지 {@code $insert_id}에 넣어 구분한다.
 */
public record BookmarkSavedEvent(
        String memberId,   // 북마크한 회원 ID — distinct_id로 쓰인다
        String artworkId,  // 북마크 대상 작품 ID
        String folderId,   // 저장된 북마크 폴더 ID. 기본 폴더면 null
        Instant occurredAt // 발생 시각 — $insert_id 구분용
) {
}
