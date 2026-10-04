package com.atcrew.artwork;

import java.time.Instant;

/**
 * 작품 업로드 실패(plans/261005-posthog-analytics-events PA-05) — analytics 모듈이 구독해
 * {@code artwork_publish_failed}로 전송한다.
 *
 * <p>업로드 트랜잭션이 롤백되는 경로에서 발행하므로 {@code @TransactionalEventListener(AFTER_ROLLBACK)}로만
 * 구독해야 한다(package-info.java 참고). 같은 회원이 짧은 시간에 반복 실패할 수 있어 {@code occurredAt}까지
 * {@code $insert_id}에 넣어 구분한다. draft 개념이 없어 draftId는 싣지 않는다(의도적 제외).
 */
public record ArtworkPublishFailedEvent(
        String memberId,      // 업로드를 시도한 회원 ID
        String errorCode,     // 실패 원인 코드 — ArtworkErrorCode 등 도메인 예외 코드명, 그 외는 예외 클래스명
        Instant occurredAt    // 발생 시각 — $insert_id 구분용
) {
}
