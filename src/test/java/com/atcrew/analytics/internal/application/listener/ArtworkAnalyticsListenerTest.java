package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.artwork.ArtworkDeletedEvent;
import com.atcrew.artwork.ArtworkEditedEvent;
import com.atcrew.artwork.ArtworkPublishFailedEvent;
import com.atcrew.artwork.ArtworkPublishedEvent;
import com.atcrew.artwork.ArtworkVisibilityChangedEvent;
import com.atcrew.artwork.BookmarkRemovedEvent;
import com.atcrew.artwork.BookmarkSavedEvent;
import com.atcrew.artwork.Visibility;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * artwork 모듈 이벤트를 분석 이벤트로 옮기는 리스너 단위 테스트(PA-05).
 */
class ArtworkAnalyticsListenerTest {

    AnalyticsEventPublisher analyticsEventPublisher = mock(AnalyticsEventPublisher.class);

    ArtworkAnalyticsListener listener = new ArtworkAnalyticsListener(analyticsEventPublisher);

    @Test
    void 작품_업로드_성공_이벤트를_artwork_published로_옮긴다() {
        listener.onArtworkPublished(new ArtworkPublishedEvent("artwork-1", "member-1", 3L, false, Visibility.PUBLIC));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("artwork_published"), eq("member-1"), properties.capture(),
                eq("artwork_published:artwork-1"));
        assertThat(properties.getValue())
                .containsEntry("upload_order", 3L)
                .containsEntry("is_first_artwork", false)
                .containsEntry("visibility", "PUBLIC");
    }

    @Test
    void 작품_업로드_실패_이벤트를_artwork_publish_failed로_옮긴다() {
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        listener.onArtworkPublishFailed(new ArtworkPublishFailedEvent("member-2", "STARTER_ARTWORK_LIMIT_EXCEEDED",
                occurredAt));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publishAnonymous(eq("artwork_publish_failed"), eq("member-2"),
                properties.capture(), eq("artwork_publish_failed:member-2:" + occurredAt.toEpochMilli()));
        assertThat(properties.getValue()).containsEntry("error_code", "STARTER_ARTWORK_LIMIT_EXCEEDED");
        // draft_id는 싣지 않는다(의도적 제외) — 키 자체가 없어야 한다.
        assertThat(properties.getValue()).doesNotContainKey("draft_id");
    }

    @Test
    void 작품_수정_이벤트를_artwork_edited로_옮긴다() {
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        listener.onArtworkEdited(new ArtworkEditedEvent("artwork-3", "member-3", List.of("title", "tags"), occurredAt));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("artwork_edited"), eq("member-3"), properties.capture(),
                eq("artwork_edited:artwork-3:" + occurredAt.toEpochMilli()));
        assertThat(properties.getValue()).containsEntry("changed_section_ids", List.of("title", "tags"));
    }

    @Test
    void 공개여부_변경_이벤트를_artwork_visibility_changed로_옮긴다() {
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        listener.onArtworkVisibilityChanged(new ArtworkVisibilityChangedEvent("artwork-4", "member-4",
                Visibility.PUBLIC, Visibility.PRIVATE, occurredAt));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("artwork_visibility_changed"), eq("member-4"), properties.capture(),
                eq("artwork_visibility_changed:artwork-4:" + occurredAt.toEpochMilli()));
        assertThat(properties.getValue())
                .containsEntry("previous_visibility", "PUBLIC")
                .containsEntry("new_visibility", "PRIVATE");
    }

    @Test
    void 작품_삭제_이벤트를_artwork_deleted로_옮긴다() {
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        listener.onArtworkDeleted(new ArtworkDeletedEvent("artwork-5", "member-5", occurredAt));

        verify(analyticsEventPublisher).publish(eq("artwork_deleted"), eq("member-5"), eq(Map.of()),
                eq("artwork_deleted:artwork-5:" + occurredAt.toEpochMilli()));
    }

    @Test
    void 북마크_저장_이벤트를_bookmark_saved로_옮긴다() {
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        listener.onBookmarkSaved(new BookmarkSavedEvent("member-6", "artwork-6", "folder-6", occurredAt));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("bookmark_saved"), eq("member-6"), properties.capture(),
                eq("bookmark_saved:member-6:artwork-6:" + occurredAt.toEpochMilli()));
        assertThat(properties.getValue())
                .containsEntry("content_type", "artwork")
                .containsEntry("content_id", "artwork-6")
                .containsEntry("folder_id", "folder-6");
    }

    @Test
    void 북마크_해제_이벤트를_bookmark_removed로_옮긴다() {
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        listener.onBookmarkRemoved(new BookmarkRemovedEvent("member-7", "artwork-7", null, occurredAt));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("bookmark_removed"), eq("member-7"), properties.capture(),
                eq("bookmark_removed:member-7:artwork-7:" + occurredAt.toEpochMilli()));
        assertThat(properties.getValue())
                .containsEntry("content_type", "artwork")
                .containsEntry("content_id", "artwork-7")
                .containsEntry("folder_id", null);
    }
}
