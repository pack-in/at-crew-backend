package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.BookmarkRemovedEvent;
import com.atcrew.artwork.BookmarkSavedEvent;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkEntry;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkFolder;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.artwork.internal.persistence.BookmarkEntryRepository;
import com.atcrew.artwork.internal.persistence.BookmarkFolderRepository;
import com.atcrew.media.MediaService;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 북마크 저장·해제 분석 이벤트 발행 단위 테스트(plans/261005-posthog-analytics-events PA-05).
 */
class BookmarkServiceImplAnalyticsEventTest {

    private static final String MEMBER_ID = "member-" + UUID.randomUUID();
    private static final String FOLDER_ID = "folder-" + UUID.randomUUID();

    BookmarkFolderRepository folderRepository = mock(BookmarkFolderRepository.class);
    BookmarkEntryRepository entryRepository = mock(BookmarkEntryRepository.class);
    ArtworkRepository artworkRepository = mock(ArtworkRepository.class);
    MemberService memberService = mock(MemberService.class);
    MediaService mediaService = mock(MediaService.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    BookmarkServiceImpl service = new BookmarkServiceImpl(folderRepository, entryRepository, artworkRepository,
            memberService, mediaService, eventPublisher);

    @BeforeEach
    void setUp() {
        when(entryRepository.save(any(BookmarkEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 북마크_저장에_성공하면_작품ID와_폴더ID를_실어_발행한다() {
        Artwork artwork = readyArtwork();
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(entryRepository.existsByMemberIdAndArtworkId(MEMBER_ID, artwork.getId())).thenReturn(false);
        when(folderRepository.findByIdAndMemberId(FOLDER_ID, MEMBER_ID))
                .thenReturn(Optional.of(BookmarkFolder.create(MEMBER_ID, "폴더", 0)));

        service.saveBookmark(MEMBER_ID, artwork.getId(), FOLDER_ID);

        ArgumentCaptor<BookmarkSavedEvent> captor = ArgumentCaptor.forClass(BookmarkSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        BookmarkSavedEvent event = captor.getValue();
        assertThat(event.memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.artworkId()).isEqualTo(artwork.getId());
        assertThat(event.folderId()).isEqualTo(FOLDER_ID);
    }

    @Test
    void 북마크_해제에_성공하면_해제_당시_폴더ID를_실어_발행한다() {
        Artwork artwork = readyArtwork();
        BookmarkEntry entry = BookmarkEntry.create(MEMBER_ID, artwork.getId(), FOLDER_ID, Visibility.PUBLIC);
        when(entryRepository.findByMemberIdAndArtworkId(MEMBER_ID, artwork.getId()))
                .thenReturn(Optional.of(entry));

        service.removeBookmark(MEMBER_ID, artwork.getId());

        ArgumentCaptor<BookmarkRemovedEvent> captor = ArgumentCaptor.forClass(BookmarkRemovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        BookmarkRemovedEvent event = captor.getValue();
        assertThat(event.memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.artworkId()).isEqualTo(artwork.getId());
        assertThat(event.folderId()).isEqualTo(FOLDER_ID);
    }

    // 다른 회원 작품이라도 READY면 북마크 대상이다 — accessFor(ALLOWED)를 만족시키려고 PUBLIC으로 만든다.
    private Artwork readyArtwork() {
        Artwork artwork = Artwork.create("author-" + UUID.randomUUID(), "제목", "설명",
                List.of("image-key-1"), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL, List.of(), List.of(), List.of(), List.of(),
                AgeRating.ALL, List.of(Language.KO), Visibility.PUBLIC, List.of(), null, null, List.of(), List.of());
        artwork.applyImageStatuses(List.of(ImageProcessingStatus.DONE));
        return artwork;
    }
}
