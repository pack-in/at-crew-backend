package com.atcrew.artwork;

import com.atcrew.media.internal.application.MediaKeySigner;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.sql.Timestamp;
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

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    BookmarkService bookmarkService;

    @Autowired
    MemberService memberService;

    @Autowired
    MediaCallbackService mediaCallbackService;

    @Autowired
    JdbcTemplate jdbcTemplate;

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

    // 이슈 #195 — size=0인데 조건에 맞는 데이터가 있으면 nextCursor 계산이 빈 page.get(-1)을
    // 호출해 500이 나던 결함의 회귀 방지.
    @Test
    void 북마크_목록_size가_0이고_데이터가_있어도_500이_나지_않는다() {
        String memberId = registerMember();
        String authorId = registerMember();
        ArtworkInfo artwork = uploadReadyArtwork(authorId);
        bookmarkService.saveBookmark(memberId, artwork.id(), null);

        CursorPage<BookmarkEntryInfo> page = bookmarkService.getBookmarks(memberId, null, null, 0);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
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

    // 목록은 savedAt 내림차순이라 가장 최근에 저장한 북마크가 앞쪽이다. 그 앞쪽이 전부 삭제된 작품이면
    // 상위 size+1건만 보고 끝내는 게 아니라 뒤쪽 북마크로 채워야 한다 — hasNext가 필터 이전 개수 기준이라
    // items는 비었는데 다음 페이지가 있다고 오판하던 회귀 버그 재현 케이스.
    @Test
    void 최근_저장한_북마크가_모두_삭제돼도_뒤쪽_북마크로_채운다() {
        String memberId = registerMember();
        // 스타터 플랜 작품 상한(마이페이지_작가-R20)이 4라 6건을 만들려면 작가를 나눈다.
        String authorId1 = registerMember();
        String authorId2 = registerMember();
        List<String> artworkIds = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ArtworkInfo artwork = uploadReadyArtwork(authorId1);
            bookmarkService.saveBookmark(memberId, artwork.id(), null);
            artworkIds.add(artwork.id());
        }
        for (int i = 0; i < 3; i++) {
            ArtworkInfo artwork = uploadReadyArtwork(authorId2);
            bookmarkService.saveBookmark(memberId, artwork.id(), null);
            artworkIds.add(artwork.id());
        }
        // artworkIds는 저장 순(오래된→최신) — 가장 최근에 저장한 2개(둘 다 authorId2 소속)를 삭제한다.
        artworkService.deleteArtwork(authorId2, artworkIds.get(5));
        artworkService.deleteArtwork(authorId2, artworkIds.get(4));

        CursorPage<BookmarkEntryInfo> page = bookmarkService.getBookmarks(memberId, null, null, 4);

        assertThat(page.items()).extracting(BookmarkEntryInfo::artworkId)
                .containsExactly(artworkIds.get(3), artworkIds.get(2), artworkIds.get(1), artworkIds.get(0));
        assertThat(page.nextCursor()).isNull();
    }

    // 저장 시각(saved_at)이 완전히 같은 행이 둘 있어도(동시 저장·일괄 반영 등) id로 동률을 갈라 양쪽 다
    // 조회돼야 한다 — 저장 시각만 엄격히 비교(<)하면 같은 시각의 한쪽이 커서 경계에서 영원히 빠지던
    // 결함의 회귀 방지.
    @Test
    void 저장_시각이_완전히_같은_북마크도_커서_경계에서_누락되지_않는다() {
        String memberId = registerMember();
        String authorId = registerMember();
        ArtworkInfo a1 = uploadReadyArtwork(authorId);
        ArtworkInfo a2 = uploadReadyArtwork(authorId);
        ArtworkInfo a3 = uploadReadyArtwork(authorId);
        bookmarkService.saveBookmark(memberId, a1.id(), null);
        bookmarkService.saveBookmark(memberId, a2.id(), null);
        bookmarkService.saveBookmark(memberId, a3.id(), null);
        copySavedAt(memberId, a3.id(), a2.id()); // a2를 a3과 완전히 같은 저장 시각으로 맞춘다.

        CursorPage<BookmarkEntryInfo> firstPage = bookmarkService.getBookmarks(memberId, null, null, 2);
        CursorPage<BookmarkEntryInfo> secondPage = bookmarkService.getBookmarks(
                memberId, null, firstPage.nextCursor(), 2);

        List<String> allIds = new java.util.ArrayList<>();
        firstPage.items().forEach(i -> allIds.add(i.artworkId()));
        secondPage.items().forEach(i -> allIds.add(i.artworkId()));
        assertThat(allIds).containsExactlyInAnyOrder(a1.id(), a2.id(), a3.id());
    }

    // 드라이버 변환을 읽기·쓰기 양쪽에 똑같이 태워 마이크로초까지 그대로 옮긴다(ArtworkSortModuleTests의
    // copyCreatedAt과 같은 이유).
    private void copySavedAt(String memberId, String fromArtworkId, String toArtworkId) {
        Timestamp savedAt = jdbcTemplate.queryForObject(
                "SELECT saved_at FROM bookmark_entries WHERE member_id = ? AND artwork_id = ?",
                Timestamp.class, memberId, fromArtworkId);
        jdbcTemplate.update("UPDATE bookmark_entries SET saved_at = ? WHERE member_id = ? AND artwork_id = ?",
                savedAt, memberId, toArtworkId);
    }

    private Class<?> catchThrowableClass(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable).getClass();
    }

    private ArtworkInfo uploadReadyArtwork(String authorId) {
        List<String> imageKeys = List.of(signedKey(authorId, UUID.randomUUID().toString()));
        ArtworkInfo artwork = artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                imageKeys, 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "북마크테스트 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
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

    /**
     * 그 회원에게 발급된 것과 같은 형태의 업로드 key(#190) — 소유 검증이 서명만 보므로 presign을 부르지 않고
     * 같은 규칙으로 만든다.
     */
    private String signedKey(String memberId, String name) {
        return "raw/" + keySigner.sign(memberId, name) + "/" + name + ".png";
    }

}
