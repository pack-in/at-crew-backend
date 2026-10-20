package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.analytics.internal.application.AnalyticsInsertId;
import com.atcrew.artwork.ArtworkDeletedEvent;
import com.atcrew.artwork.ArtworkEditedEvent;
import com.atcrew.artwork.ArtworkPublishFailedEvent;
import com.atcrew.artwork.ArtworkPublishedEvent;
import com.atcrew.artwork.ArtworkVisibilityChangedEvent;
import com.atcrew.artwork.BookmarkRemovedEvent;
import com.atcrew.artwork.BookmarkSavedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * artwork 모듈(작품·북마크) 이벤트를 분석 이벤트로 옮긴다(plans/261005-posthog-analytics-events PA-05).
 *
 * <p>패턴(성공/실패 리스너 구분, $insert_id)은 package-info.java 참고.
 */
@Component
class ArtworkAnalyticsListener {

    private static final String ARTWORK_PUBLISHED = "artwork_published";
    private static final String ARTWORK_PUBLISH_FAILED = "artwork_publish_failed";
    private static final String ARTWORK_EDITED = "artwork_edited";
    private static final String ARTWORK_VISIBILITY_CHANGED = "artwork_visibility_changed";
    private static final String ARTWORK_DELETED = "artwork_deleted";
    private static final String BOOKMARK_SAVED = "bookmark_saved";
    private static final String BOOKMARK_REMOVED = "bookmark_removed";

    // 북마크 대상은 현재 작품뿐이다 — 다른 콘텐츠 타입이 생기면 이벤트에 필드로 실어야 한다.
    private static final String BOOKMARK_CONTENT_TYPE_ARTWORK = "artwork";

    private final AnalyticsEventPublisher analyticsEventPublisher;

    ArtworkAnalyticsListener(AnalyticsEventPublisher analyticsEventPublisher) {
        this.analyticsEventPublisher = analyticsEventPublisher;
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void onArtworkPublished(ArtworkPublishedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("upload_order", event.uploadOrder());
        properties.put("is_first_artwork", event.isFirstArtwork());
        properties.put("visibility", event.visibility().name());
        analyticsEventPublisher.publish(ARTWORK_PUBLISHED, event.authorId(), properties,
                AnalyticsInsertId.of(ARTWORK_PUBLISHED, event.artworkId()), null);
    }

    /**
     * 업로드 트랜잭션이 롤백되는 경로에서 발행되므로 레지스트리를 거치지 않는 {@code AFTER_ROLLBACK}
     * 리스너로 받고, 실패 응답을 늦추지 않도록 별도 스레드에서 보낸다(package-info.java 참고) — 재시도는 없다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    void onArtworkPublishFailed(ArtworkPublishFailedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("error_code", event.errorCode());
        analyticsEventPublisher.publish(ARTWORK_PUBLISH_FAILED, event.memberId(), properties,
                AnalyticsInsertId.of(ARTWORK_PUBLISH_FAILED, event.memberId(), event.occurredAt()),
                event.occurredAt());
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void onArtworkEdited(ArtworkEditedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("changed_section_ids", event.changedSectionIds());
        analyticsEventPublisher.publish(ARTWORK_EDITED, event.authorId(), properties,
                AnalyticsInsertId.of(ARTWORK_EDITED, event.artworkId(), event.occurredAt()), event.occurredAt());
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void onArtworkVisibilityChanged(ArtworkVisibilityChangedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("previous_visibility", event.previousVisibility().name());
        properties.put("new_visibility", event.newVisibility().name());
        analyticsEventPublisher.publish(ARTWORK_VISIBILITY_CHANGED, event.authorId(), properties,
                AnalyticsInsertId.of(ARTWORK_VISIBILITY_CHANGED, event.artworkId(), event.occurredAt()),
                event.occurredAt());
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void onArtworkDeleted(ArtworkDeletedEvent event) {
        analyticsEventPublisher.publish(ARTWORK_DELETED, event.authorId(), Map.of(),
                AnalyticsInsertId.of(ARTWORK_DELETED, event.artworkId(), event.occurredAt()), event.occurredAt());
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void onBookmarkSaved(BookmarkSavedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("content_type", BOOKMARK_CONTENT_TYPE_ARTWORK);
        properties.put("content_id", event.artworkId());
        properties.put("folder_id", event.folderId());
        analyticsEventPublisher.publish(BOOKMARK_SAVED, event.memberId(), properties,
                AnalyticsInsertId.of(BOOKMARK_SAVED, event.memberId() + ":" + event.artworkId(), event.occurredAt()),
                event.occurredAt());
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void onBookmarkRemoved(BookmarkRemovedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("content_type", BOOKMARK_CONTENT_TYPE_ARTWORK);
        properties.put("content_id", event.artworkId());
        properties.put("folder_id", event.folderId());
        analyticsEventPublisher.publish(BOOKMARK_REMOVED, event.memberId(), properties,
                AnalyticsInsertId.of(BOOKMARK_REMOVED, event.memberId() + ":" + event.artworkId(),
                        event.occurredAt()), event.occurredAt());
    }
}
