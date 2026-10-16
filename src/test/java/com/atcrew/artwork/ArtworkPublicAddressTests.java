package com.atcrew.artwork;

import com.atcrew.SharedContainersConfig;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.response.OffsetPage;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.media.internal.application.MediaKeySigner;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 작품 공개 주소 {@code /{handle}/artwork/{code}} 조회와 응답의 주소 조립 필드 검증
 * (docs/design/member-handle-design.md §2.2, §3.4).
 *
 * <p>접근 판정은 ID 상세 조회({@link ArtworkService#getArtwork})와 같아야 하므로, 판정 케이스는 두 경로가 같은 오류를
 * 내는지 함께 확인한다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class ArtworkPublicAddressTests {

    @Autowired
    ArtworkService artworkService;

    @Autowired
    BookmarkService bookmarkService;

    @Autowired
    MemberService memberService;

    // 제3자 열람 판정은 READY가 전제라 Worker webhook 경로로 처리 완료까지 올린다.
    @Autowired
    MediaCallbackService mediaCallbackService;

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

    // ─── 공개 주소 조회 ───────────────────────────────────────────

    @Test
    void 현재_handle과_코드로_작품을_조회하면_주소_조립_정보가_담긴다() {
        String authorId = registerAuthor();
        ArtworkInfo uploaded = uploadReady(authorId, true);
        String handle = handleOf(authorId);

        ArtworkInfo found = artworkService.getArtworkByPublicCode(handle, uploaded.publicCode(), null);

        assertThat(found.id()).isEqualTo(uploaded.id());
        assertThat(found.authorHandle()).isEqualTo(handle);
        assertThat(found.publicCode()).isEqualTo(uploaded.publicCode()).matches("^[a-z0-9]{6}$");
    }

    @Test
    void handle_조회는_대소문자를_가리지_않는다() {
        String authorId = registerAuthor();
        ArtworkInfo uploaded = uploadReady(authorId, true);
        String handle = handleOf(authorId);

        ArtworkInfo found = artworkService.getArtworkByPublicCode(handle.toUpperCase(), uploaded.publicCode(), null);

        assertThat(found.id()).isEqualTo(uploaded.id());
        assertThat(found.authorHandle()).isEqualTo(handle);
    }

    @Test
    void 옛_handle로_조회해도_응답에는_현재_handle이_담긴다() {
        String authorId = registerAuthor();
        ArtworkInfo uploaded = uploadReady(authorId, true);
        String oldHandle = handleOf(authorId);
        String newHandle = "nw" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        memberService.changeHandle(authorId, newHandle);

        ArtworkInfo found = artworkService.getArtworkByPublicCode(oldHandle, uploaded.publicCode(), null);

        assertThat(found.id()).isEqualTo(uploaded.id());
        assertThat(found.authorHandle()).isEqualTo(newHandle);
    }

    @Test
    void 탈퇴한_작가의_handle은_410_MEMBER_WITHDRAWN() {
        String authorId = registerAuthor();
        ArtworkInfo uploaded = uploadReady(authorId, true);
        String handle = handleOf(authorId);
        memberService.deactivate(authorId);

        assertThatThrownBy(() -> artworkService.getArtworkByPublicCode(handle, uploaded.publicCode(), null))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("MEMBER_WITHDRAWN");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.GONE);
                });
    }

    @Test
    void 쓰인_적_없는_handle은_404_MEMBER_NOT_FOUND() {
        assertThatThrownBy(() -> artworkService.getArtworkByPublicCode("nobody_here", "abc123", null))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("MEMBER_NOT_FOUND");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void 그_작가에게_없는_코드는_404_ARTWORK_NOT_FOUND() {
        String authorId = registerAuthor();
        uploadReady(authorId, true);
        String handle = handleOf(authorId);

        assertNotFound(() -> artworkService.getArtworkByPublicCode(handle, "zzzzzz", null));
    }

    // 코드는 작가 안에서만 유일하다 — 다른 작가 handle에 붙이면 그 작가 범위에서 찾으므로 열리지 않아야 한다.
    @Test
    void 다른_작가의_코드를_붙이면_404_ARTWORK_NOT_FOUND() {
        String authorA = registerAuthor();
        String authorB = registerAuthor();
        ArtworkInfo ofB = uploadReady(authorB, true);

        assertNotFound(() -> artworkService.getArtworkByPublicCode(handleOf(authorA), ofB.publicCode(), null));
    }

    @Test
    void 완전_비공개_작품은_ID_조회와_같이_403_ARTWORK_PRIVATE() {
        String authorId = registerAuthor();
        String viewerId = registerAuthor();
        ArtworkInfo hidden = uploadReady(authorId, false);
        String handle = handleOf(authorId);

        assertThatThrownBy(() -> artworkService.getArtwork(hidden.id(), viewerId))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("ARTWORK_PRIVATE");
        assertThatThrownBy(() -> artworkService.getArtworkByPublicCode(handle, hidden.publicCode(), viewerId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("ARTWORK_PRIVATE");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        // 본인은 ID 조회와 마찬가지로 열람할 수 있다.
        assertThat(artworkService.getArtworkByPublicCode(handle, hidden.publicCode(), authorId).id())
                .isEqualTo(hidden.id());
    }

    @Test
    void 휴지통_작품은_ID_조회와_같이_410_ARTWORK_DELETED() {
        String authorId = registerAuthor();
        ArtworkInfo trashed = uploadReady(authorId, true);
        String handle = handleOf(authorId);
        artworkService.deleteArtwork(authorId, trashed.id());

        assertThatThrownBy(() -> artworkService.getArtwork(trashed.id(), null))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("ARTWORK_DELETED");
        assertThatThrownBy(() -> artworkService.getArtworkByPublicCode(handle, trashed.publicCode(), null))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("ARTWORK_DELETED");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.GONE);
                });
    }

    // ─── 기존 응답의 주소 조립 필드 ─────────────────────────────────

    // 옛 /artwork/{UUID} 링크 — FE가 이 응답으로 새 주소를 조립해 308한다.
    @Test
    void ID_상세_조회_응답에도_현재_handle과_코드가_담긴다() {
        String authorId = registerAuthor();
        ArtworkInfo uploaded = uploadReady(authorId, true);
        String newHandle = "nw" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        memberService.changeHandle(authorId, newHandle);

        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), null);

        assertThat(found.authorHandle()).isEqualTo(newHandle);
        assertThat(found.publicCode()).isEqualTo(uploaded.publicCode());
    }

    @Test
    void 카드_목록에도_현재_handle과_코드가_담긴다() {
        String authorId = registerAuthor();
        String viewerId = registerAuthor();
        ArtworkInfo uploaded = uploadReady(authorId, true);
        String newHandle = "nw" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        memberService.changeHandle(authorId, newHandle);

        ArtworkSummaryInfo mine = artworkService.getMyArtworks(authorId, null, 20).items().getFirst();
        assertThat(mine.publicCode()).isEqualTo(uploaded.publicCode());
        assertThat(mine.authorHandle()).isEqualTo(newHandle);

        OffsetPage<ArtworkSummaryInfo> feed = artworkService.getCommunityArtworks(
                null, null, List.of(), ArtworkSort.LATEST, 1, 50, null, true);
        ArtworkSummaryInfo inFeed = feed.items().stream()
                .filter(card -> card.id().equals(uploaded.id())).findFirst().orElseThrow();
        assertThat(inFeed.publicCode()).isEqualTo(uploaded.publicCode());
        assertThat(inFeed.authorHandle()).isEqualTo(newHandle);

        ArtworkSummaryInfo bookmarked = bookmarkService.saveBookmark(viewerId, uploaded.id(), null).artwork();
        assertThat(bookmarked.publicCode()).isEqualTo(uploaded.publicCode());
        assertThat(bookmarkService.getBookmarks(viewerId, null, null, 20).items().getFirst().artwork().publicCode())
                .isEqualTo(uploaded.publicCode());
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("ARTWORK_NOT_FOUND");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    /** 업로드 후 webhook으로 READY까지 올린다. {@code publishToFeed=false}면 어느 포트폴리오에도 없어 완전 비공개다. */
    private ArtworkInfo uploadReady(String authorId, boolean publishToFeed) {
        String name = UUID.randomUUID().toString();
        String key = "raw/" + keySigner.sign(authorId, name) + "/" + name + ".png";
        ArtworkInfo uploaded = artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                List.of(key), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), publishToFeed, List.of(), List.of(), null, null, List.of(),
                List.of()));
        mediaCallbackService.process(MediaOwnerType.ARTWORK, uploaded.id(), key,
                null, null, "original/" + name + ".avif", MediaProcessingStatus.DONE);
        awaitReady(authorId, uploaded.id());
        return uploaded;
    }

    /** artwork 리스너는 @ApplicationModuleListener(비동기)라 READY 반영까지 폴링한다. */
    private void awaitReady(String authorId, String artworkId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
        while (Instant.now().isBefore(deadline)) {
            if (artworkService.getArtworkStatus(authorId, artworkId) == ArtworkStatus.READY) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("READY 전환 대기 시간 초과");
    }

    private String handleOf(String memberId) {
        return memberService.findById(memberId).handle();
    }

    private String registerAuthor() {
        return memberService.register(
                "addr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "ad" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "작가").id();
    }
}
