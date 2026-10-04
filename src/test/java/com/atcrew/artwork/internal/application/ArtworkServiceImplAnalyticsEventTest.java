package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkDeletedEvent;
import com.atcrew.artwork.ArtworkEditedEvent;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkPublishFailedEvent;
import com.atcrew.artwork.ArtworkPublishedEvent;
import com.atcrew.artwork.ArtworkVisibilityChangedEvent;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.UpdateArtworkCommand;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.billing.BillingService;
import com.atcrew.media.MediaService;
import com.atcrew.member.Language;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * artwork 모듈 분석 이벤트 발행 단위 테스트(plans/261005-posthog-analytics-events PA-05).
 *
 * <p>실제 PostHog 전송(analytics 모듈)은 거치지 않는다 — {@link ApplicationEventPublisher}를 모킹해
 * 이벤트가 올바른 값으로 발행되는지만 본다. 한 호출에서 여러 이벤트(예: {@code ArtworkChangedEvent}와 함께)가
 * 나가므로 {@link #capturedEvent(Class)}로 원하는 타입만 걸러낸다.
 */
class ArtworkServiceImplAnalyticsEventTest {

    private static final String MEMBER_ID = "member-" + UUID.randomUUID();

    ArtworkRepository artworkRepository = mock(ArtworkRepository.class);
    MemberService memberService = mock(MemberService.class);
    MediaService mediaService = mock(MediaService.class);
    BillingService billingService = mock(BillingService.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    ArtworkPurger artworkPurger = mock(ArtworkPurger.class);
    ArtworkViewRecorder artworkViewRecorder = mock(ArtworkViewRecorder.class);

    ArtworkServiceImpl service = new ArtworkServiceImpl(artworkRepository, memberService, mediaService,
            billingService, eventPublisher, artworkPurger, artworkViewRecorder);

    @BeforeEach
    void setUp() {
        // save()는 새로 만든 엔티티를 그대로 돌려준다 — Artwork.create()가 이미 ID를 할당해 둔다.
        when(artworkRepository.save(any(Artwork.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberService.findById(MEMBER_ID)).thenReturn(memberInfo(MEMBER_ID));
    }

    @Test
    void 업로드에_성공하면_업로드_순번과_공개여부를_실어_발행한다() {
        when(artworkRepository.countByAuthorIdAndStatusNot(eq(MEMBER_ID), any())).thenReturn(3L);

        service.uploadArtwork(MEMBER_ID, uploadCommand(true));

        ArtworkPublishedEvent event = capturedEvent(ArtworkPublishedEvent.class);
        assertThat(event.authorId()).isEqualTo(MEMBER_ID);
        assertThat(event.uploadOrder()).isEqualTo(3L);
        assertThat(event.isFirstArtwork()).isFalse();
        assertThat(event.visibility()).isEqualTo(Visibility.PUBLIC);
        verify(eventPublisher, never()).publishEvent(any(ArtworkPublishFailedEvent.class));
    }

    @Test
    void 첫_업로드는_isFirstArtwork를_참으로_발행한다() {
        when(artworkRepository.countByAuthorIdAndStatusNot(eq(MEMBER_ID), any())).thenReturn(1L);

        service.uploadArtwork(MEMBER_ID, uploadCommand(true));

        assertThat(capturedEvent(ArtworkPublishedEvent.class).isFirstArtwork()).isTrue();
    }

    // 업로드 중 예외가 나면(여기서는 스타터 한도 초과) artwork_publish_failed만 내고 성공 이벤트는 내지 않는다.
    // draft_id는 싣지 않는다(의도적 제외, plans/261005-posthog-analytics-events 참고).
    @Test
    void 업로드가_실패하면_에러코드를_실어_실패_이벤트를_발행하고_예외를_다시_던진다() {
        when(artworkRepository.findByAuthorIdAndStatusNotInForUpdate(eq(MEMBER_ID), any()))
                .thenReturn(List.of(mock(Artwork.class), mock(Artwork.class), mock(Artwork.class), mock(Artwork.class)));
        when(billingService.hasProPlan(MEMBER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.uploadArtwork(MEMBER_ID, uploadCommand(true)))
                .isInstanceOf(ArtworkException.class);

        ArtworkPublishFailedEvent event = capturedEvent(ArtworkPublishFailedEvent.class);
        assertThat(event.memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.errorCode()).isEqualTo(ArtworkErrorCode.STARTER_ARTWORK_LIMIT_EXCEEDED.name());
        verify(eventPublisher, never()).publishEvent(any(ArtworkPublishedEvent.class));
    }

    @Test
    void 제목만_수정하면_changedSectionIds에_title만_담아_발행한다() {
        Artwork artwork = existingArtwork();
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));

        service.updateArtwork(MEMBER_ID, artwork.getId(), updateTitleOnlyCommand("새 제목"));

        ArtworkEditedEvent event = capturedEvent(ArtworkEditedEvent.class);
        assertThat(event.artworkId()).isEqualTo(artwork.getId());
        assertThat(event.authorId()).isEqualTo(MEMBER_ID);
        assertThat(event.changedSectionIds()).containsExactly("title");
    }

    @Test
    void 같은_제목으로_수정하면_changedSectionIds가_비어있다() {
        Artwork artwork = existingArtwork();
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));

        service.updateArtwork(MEMBER_ID, artwork.getId(), updateTitleOnlyCommand(artwork.getTitle()));

        assertThat(capturedEvent(ArtworkEditedEvent.class).changedSectionIds()).isEmpty();
    }

    @Test
    void 공개여부가_바뀌면_이전값과_새값을_실어_발행한다() {
        Artwork artwork = existingArtwork(); // PUBLIC
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));

        service.updatePublication(MEMBER_ID, artwork.getId(), false, List.of());

        ArtworkVisibilityChangedEvent event = capturedEvent(ArtworkVisibilityChangedEvent.class);
        assertThat(event.previousVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(event.newVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void 공개여부가_바뀌지_않으면_이벤트를_발행하지_않는다() {
        Artwork artwork = existingArtwork(); // PUBLIC
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));

        service.updatePublication(MEMBER_ID, artwork.getId(), true, List.of());

        verify(eventPublisher, never()).publishEvent(any(ArtworkVisibilityChangedEvent.class));
    }

    @Test
    void 삭제하면_작품ID와_회원ID를_실어_발행한다() {
        Artwork artwork = existingArtwork();
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));

        service.deleteArtwork(MEMBER_ID, artwork.getId());

        ArtworkDeletedEvent event = capturedEvent(ArtworkDeletedEvent.class);
        assertThat(event.artworkId()).isEqualTo(artwork.getId());
        assertThat(event.authorId()).isEqualTo(MEMBER_ID);
    }

    /** 한 호출에서 여러 이벤트가 나갈 수 있어, 그중 원하는 타입 하나만 골라낸다(정확히 하나여야 한다). */
    @SuppressWarnings("unchecked")
    private <T> T capturedEvent(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        List<T> matched = captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
        assertThat(matched).as(type.getSimpleName() + " 발행 횟수").hasSize(1);
        return matched.get(0);
    }

    private Artwork existingArtwork() {
        Artwork artwork = Artwork.create(MEMBER_ID, "기존 제목", "기존 설명",
                List.of("image-key-1"), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL, List.of(), List.of(), List.of(), List.of(),
                AgeRating.ALL, List.of(Language.KO), Visibility.PUBLIC, List.of(), null, null, List.of(), List.of());
        artwork.applyImageStatuses(List.of(ImageProcessingStatus.DONE));
        return artwork;
    }

    private UpdateArtworkCommand updateTitleOnlyCommand(String title) {
        return new UpdateArtworkCommand(null, null, null, null, title, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    private UploadArtworkCommand uploadCommand(boolean publishToFeed) {
        return new UploadArtworkCommand(
                List.of("image-key-1"), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "제목", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), List.of(), List.of(),
                AgeRating.ALL, List.of(Language.KO), publishToFeed, List.of(),
                List.of(), null, null, List.of(), List.of());
    }

    private static MemberInfo memberInfo(String id) {
        return new MemberInfo(
                id, "handle-" + id, null, null, "작가", null, List.of(), null, null, List.of(),
                List.of(), null, null, List.of(), List.of(), List.of(), null, List.of(), null, null,
                List.of(), 0, 0, null, null, null, List.of(), true, null, null, null, null,
                null, null, Language.KO, List.of(), false, false);
    }
}
