package com.atcrew.artwork;

import com.atcrew.SharedContainersConfig;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import com.atcrew.common.response.CursorPage;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * bookmark(북마크 폴더·항목) 모듈 MariaDB 전환 검증 — Mongo Criteria 커서 페이지네이션이
 * Spring Data JPA 파생 쿼리(§3.6)로 정확히 이식됐는지 확인한다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class BookmarkModuleTests {

    @Autowired
    ArtworkService artworkService;

    @Autowired
    BookmarkService bookmarkService;

    @Autowired
    MemberService memberService;

    @Autowired
    MediaCallbackService mediaCallbackService;

    @Test
    void 폴더_생성_후_목록에서_조회된다() {
        String memberId = registerMember();

        BookmarkFolderInfo folder = bookmarkService.createFolder(memberId, "즐겨찾기");

        assertThat(bookmarkService.getFolders(memberId)).extracting(BookmarkFolderInfo::id).contains(folder.id());
    }

    @Test
    void 폴더_삭제하면_소속_북마크가_기본_폴더로_이동한다() {
        String memberId = registerMember();
        String authorId = registerMember();
        ArtworkInfo artwork = uploadReadyArtwork(authorId);
        BookmarkFolderInfo folder = bookmarkService.createFolder(memberId, "폴더1");
        bookmarkService.saveBookmark(memberId, artwork.id(), folder.id());

        bookmarkService.deleteFolder(memberId, folder.id());

        CursorPage<BookmarkEntryInfo> page = bookmarkService.getBookmarks(memberId, null, null, 10);
        assertThat(page.items()).extracting(BookmarkEntryInfo::artworkId).contains(artwork.id());
    }

    @Test
    void 북마크_저장_후_커서_페이지네이션으로_조회된다() {
        String memberId = registerMember();
        String authorId = registerMember();
        List<ArtworkInfo> artworks = List.of(
                uploadReadyArtwork(authorId), uploadReadyArtwork(authorId), uploadReadyArtwork(authorId));
        artworks.forEach(a -> bookmarkService.saveBookmark(memberId, a.id(), null));

        CursorPage<BookmarkEntryInfo> firstPage = bookmarkService.getBookmarks(memberId, null, null, 2);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.nextCursor()).isNotNull();

        CursorPage<BookmarkEntryInfo> secondPage = bookmarkService.getBookmarks(
                memberId, null, firstPage.nextCursor(), 2);
        assertThat(secondPage.items()).hasSize(1);

        List<String> allIds = new java.util.ArrayList<>();
        firstPage.items().forEach(i -> allIds.add(i.id()));
        secondPage.items().forEach(i -> allIds.add(i.id()));
        assertThat(allIds).doesNotHaveDuplicates().hasSize(3);
    }

    @Test
    void 중복_북마크는_거부된다() {
        String memberId = registerMember();
        String authorId = registerMember();
        ArtworkInfo artwork = uploadReadyArtwork(authorId);
        bookmarkService.saveBookmark(memberId, artwork.id(), null);

        assertThat(catchThrowableClass(() -> bookmarkService.saveBookmark(memberId, artwork.id(), null)))
                .isNotNull();
    }

    @Test
    void 북마크_제거_후_목록에서_사라진다() {
        String memberId = registerMember();
        String authorId = registerMember();
        ArtworkInfo artwork = uploadReadyArtwork(authorId);
        bookmarkService.saveBookmark(memberId, artwork.id(), null);

        bookmarkService.removeBookmark(memberId, artwork.id());

        CursorPage<BookmarkEntryInfo> page = bookmarkService.getBookmarks(memberId, null, null, 10);
        assertThat(page.items()).extracting(BookmarkEntryInfo::artworkId).doesNotContain(artwork.id());
    }

    // 저장 기준(accessFor)과 목록 기준이 어긋나면 저장은 되는데 목록에는 영원히 안 보이는 북마크가 생긴다.
    @Test
    void 포트폴리오_한정_공개_작품도_북마크_목록에_보인다() {
        String memberId = registerMember();
        String authorId = registerMember();
        ArtworkInfo artwork = uploadReadyArtwork(authorId);
        // 피드 공개 OFF + 라이브 포트폴리오 편입 = 포트폴리오 한정 공개(업로드-R09).
        artworkService.updatePublication(authorId, artwork.id(), false, List.of());
        artworkService.updatePortfolioInclusion(artwork.id(), true);

        bookmarkService.saveBookmark(memberId, artwork.id(), null);

        assertThat(bookmarkService.getBookmarks(memberId, null, null, 10).items())
                .extracting(BookmarkEntryInfo::artworkId)
                .containsExactly(artwork.id());
    }

    // 반대로 편입이 풀려 완전 비공개가 되면 목록에서도 빠져야 한다 — 목록은 현재 접근 권한을 따른다.
    @Test
    void 완전_비공개로_바뀐_작품은_북마크_목록에서_빠진다() {
        String memberId = registerMember();
        String authorId = registerMember();
        ArtworkInfo artwork = uploadReadyArtwork(authorId);
        artworkService.updatePublication(authorId, artwork.id(), false, List.of());
        artworkService.updatePortfolioInclusion(artwork.id(), true);
        bookmarkService.saveBookmark(memberId, artwork.id(), null);

        artworkService.updatePortfolioInclusion(artwork.id(), false);

        assertThat(bookmarkService.getBookmarks(memberId, null, null, 10).items())
                .extracting(BookmarkEntryInfo::artworkId)
                .doesNotContain(artwork.id());
    }

    private Class<?> catchThrowableClass(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable).getClass();
    }

    private ArtworkInfo uploadReadyArtwork(String authorId) {
        List<String> imageKeys = List.of("raw/" + UUID.randomUUID() + ".png");
        ArtworkInfo artwork = artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                imageKeys, 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "북마크테스트 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
        // media webhook → MediaAssetProcessedEvent → artwork 리스너(비동기)로 READY 전환된다.
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artwork.id(), imageKeys.get(0),
                "thumb", null, "avif", MediaProcessingStatus.DONE);
        awaitReady(authorId, artwork.id());
        return artworkService.getArtwork(artwork.id(), authorId);
    }

    /** artwork 리스너는 @ApplicationModuleListener(비동기)라 READY 반영까지 폴링한다. */
    private void awaitReady(String memberId, String artworkId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
        while (Instant.now().isBefore(deadline)) {
            if (artworkService.getArtworkStatus(memberId, artworkId) == ArtworkStatus.READY) return;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("READY 전환 대기 시간 초과");
    }

    private String registerMember() {
        return memberService.register(
                "bookmark-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "bm" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "회원").id();
    }
}
