package com.atcrew.artwork;

import com.atcrew.SharedContainersConfig;
import com.atcrew.common.response.CursorPage;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 커뮤니티 포트폴리오 목록 정렬(이슈 #78) 검증 — 조회수·북마크 수 집계와 정렬 기준별 커서 페이지네이션.
 *
 * <p>핵심은 <b>정렬값이 같은 레코드가 여러 개일 때도 페이지 경계에서 누락·중복이 없는가</b>다.
 * 조회수·북마크 수는 신규 작품이 전부 0이라 동률이 예외가 아니라 기본 상황이다.
 *
 * <p>테스트끼리 피드가 섞이지 않도록 시나리오마다 서로 다른 {@link ArtworkField}를 쓰고 목록 조회 시
 * 그 분야로 필터한다(테스트 클래스 안에서는 DB가 공유된다 — {@link DatabaseCleanupExtension}).
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class ArtworkSortModuleTests {

    /** 스타터 플랜 작품 상한(마이페이지_작가-R20)이 4라 6건을 만들려면 작가를 나눠야 한다. */
    private static final int ARTWORKS_PER_AUTHOR = 3;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    BookmarkService bookmarkService;

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 작품_상세_조회는_본인을_빼고_타인과_비로그인_조회만_센다() {
        String author = registerMember();
        String viewer = registerMember();
        String artworkId = publishReady(author, ArtworkField.ANIMATION, "raw/view-1.png");

        artworkService.getArtwork(artworkId, author);
        assertThat(viewCountOf(artworkId)).isZero();

        artworkService.getArtwork(artworkId, viewer);
        assertThat(viewCountOf(artworkId)).isEqualTo(1L);

        // 비로그인 조회도 남의 조회다 — dedup 없이 열람마다 증가한다.
        artworkService.getArtwork(artworkId, null);
        assertThat(viewCountOf(artworkId)).isEqualTo(2L);

        // 같은 사람이 다시 봐도 계속 증가한다(단순 증가 정책).
        artworkService.getArtwork(artworkId, viewer);
        assertThat(viewCountOf(artworkId)).isEqualTo(3L);
    }

    @Test
    void 북마크_저장과_해제로_북마크수가_증감한다() {
        String author = registerMember();
        String member = registerMember();
        String artworkId = publishReady(author, ArtworkField.ETC, "raw/bookmark-1.png");

        bookmarkService.saveBookmark(member, artworkId, null);
        assertThat(bookmarkCountOf(artworkId)).isEqualTo(1L);

        bookmarkService.removeBookmark(member, artworkId);
        assertThat(bookmarkCountOf(artworkId)).isZero();
    }

    @Test
    void 북마크수가_이미_0이면_해제해도_음수로_내려가지_않는다() {
        String author = registerMember();
        String member = registerMember();
        String artworkId = publishReady(author, ArtworkField.ETC, "raw/bookmark-2.png");
        bookmarkService.saveBookmark(member, artworkId, null);

        // 동시 해제나 마이그레이션 유입분처럼 저장 이력과 카운터가 어긋난 상태를 재현한다.
        jdbcTemplate.update("UPDATE artworks SET bookmark_count = 0 WHERE id = ?", artworkId);

        bookmarkService.removeBookmark(member, artworkId);

        assertThat(bookmarkCountOf(artworkId)).isZero();
    }

    @Test
    void 최신순과_오래된순은_등록일이_같은_구간에서도_누락이나_중복_없이_전체를_돌려준다() {
        ArtworkField field = ArtworkField.PRINT_COMIC;
        List<String> artworkIds = publishReadyArtworks(field, 6, "raw/date-");
        // 등록일이 겹치는 구간을 강제로 만든다 — 순차 업로드만으로는 마이크로초까지 모두 달라진다.
        // 동률 묶음을 3건씩 만들어 페이지 크기(2)와 어긋나게 한다. 묶음 크기가 페이지 크기와 같으면
        // 동률 구간이 항상 페이지 경계에 딱 떨어져 tiebreaker가 없어도 통과해버린다.
        copyCreatedAt(artworkIds.get(0), artworkIds.get(1));
        copyCreatedAt(artworkIds.get(0), artworkIds.get(2));
        copyCreatedAt(artworkIds.get(3), artworkIds.get(4));
        copyCreatedAt(artworkIds.get(3), artworkIds.get(5));

        List<String> latestPaged = pageThrough(field, ArtworkSort.LATEST, 2);
        List<String> latestSinglePage = pageThrough(field, ArtworkSort.LATEST, 50);
        List<String> oldestPaged = pageThrough(field, ArtworkSort.OLDEST, 2);
        List<String> oldestSinglePage = pageThrough(field, ArtworkSort.OLDEST, 50);

        assertThat(latestPaged).containsExactlyInAnyOrderElementsOf(artworkIds);
        assertThat(latestPaged).isEqualTo(latestSinglePage);
        assertThat(oldestPaged).containsExactlyInAnyOrderElementsOf(artworkIds);
        assertThat(oldestPaged).isEqualTo(oldestSinglePage);
        // 최신순과 오래된순은 (등록일, id) 전순서의 정확한 역순이라 뒤집으면 서로 같아야 한다.
        assertThat(oldestPaged).isEqualTo(latestPaged.reversed());
        // 실제로 등록일 내림차순인지도 확인한다 — 순회가 일관돼도 정렬 키가 틀릴 수 있다.
        assertThat(latestPaged.stream().map(this::createdAtOf).toList())
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void 조회순은_조회수가_같은_구간에서도_누락이나_중복_없이_전체를_돌려준다() {
        ArtworkField field = ArtworkField.ILLUSTRATION;
        List<String> artworkIds = publishReadyArtworks(field, 6, "raw/views-");
        // 동률 묶음(3건)이 페이지 크기(2)와 어긋나게 배치한다 — 딱 떨어지면 tiebreaker 없이도 통과한다.
        // 조회수 0인 신규 작품이 섞이는 실제 상황도 함께 재현한다.
        Map<String, Long> viewCounts = Map.of(
                artworkIds.get(0), 7L, artworkIds.get(1), 7L, artworkIds.get(2), 7L,
                artworkIds.get(3), 3L, artworkIds.get(4), 3L,
                artworkIds.get(5), 0L);
        viewCounts.forEach((id, count) ->
                jdbcTemplate.update("UPDATE artworks SET view_count = ? WHERE id = ?", count, id));

        List<String> paged = pageThrough(field, ArtworkSort.VIEW_COUNT, 2);

        assertThat(paged).isEqualTo(expectedOrder(artworkIds, viewCounts));
        assertThat(paged).isEqualTo(pageThrough(field, ArtworkSort.VIEW_COUNT, 50));
    }

    @Test
    void 북마크순은_북마크수가_같은_구간에서도_누락이나_중복_없이_전체를_돌려준다() {
        ArtworkField field = ArtworkField.WEBTOON;
        List<String> artworkIds = publishReadyArtworks(field, 6, "raw/bookmarks-");
        Map<String, Long> bookmarkCounts = Map.of(
                artworkIds.get(0), 4L, artworkIds.get(1), 4L, artworkIds.get(2), 4L,
                artworkIds.get(3), 1L, artworkIds.get(4), 0L, artworkIds.get(5), 0L);
        bookmarkCounts.forEach((id, count) ->
                jdbcTemplate.update("UPDATE artworks SET bookmark_count = ? WHERE id = ?", count, id));

        List<String> paged = pageThrough(field, ArtworkSort.BOOKMARK_COUNT, 2);

        assertThat(paged).isEqualTo(expectedOrder(artworkIds, bookmarkCounts));
        assertThat(paged).isEqualTo(pageThrough(field, ArtworkSort.BOOKMARK_COUNT, 50));
    }

    /** 커서를 따라 끝까지 순회하며 나온 순서대로 작품 ID를 모은다. */
    private List<String> pageThrough(ArtworkField field, ArtworkSort sort, int pageSize) {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        // 커서가 진전되지 않아 같은 페이지를 무한히 도는 회귀를 테스트가 매달리지 않고 잡도록 상한을 둔다.
        for (int page = 0; page < 50; page++) {
            CursorPage<ArtworkSummaryInfo> result = artworkService.getCommunityArtworks(
                    field, null, List.of(), sort, cursor, pageSize, null, true);
            ids.addAll(result.items().stream().map(ArtworkSummaryInfo::id).toList());
            cursor = result.nextCursor();
            if (cursor == null) {
                return ids;
            }
        }
        throw new AssertionError("커서 순회가 끝나지 않았습니다: sort=" + sort);
    }

    /** 기대 정렬 순서 — (집계값 내림차순, 작품 ID 내림차순). 서비스 구현과 독립된 비교 기준이다. */
    private List<String> expectedOrder(List<String> artworkIds, Map<String, Long> counts) {
        Comparator<String> ascending = Comparator.<String>comparingLong(counts::get)
                .thenComparing(Comparator.naturalOrder());
        return artworkIds.stream().sorted(ascending.reversed()).toList();
    }

    private List<String> publishReadyArtworks(ArtworkField field, int count, String keyPrefix) {
        List<String> artworkIds = new ArrayList<>();
        String author = registerMember();
        for (int i = 0; i < count; i++) {
            if (i > 0 && i % ARTWORKS_PER_AUTHOR == 0) {
                author = registerMember();
            }
            artworkIds.add(publishReady(author, field, keyPrefix + i + ".png"));
        }
        return artworkIds;
    }

    /**
     * 피드에 노출되는 작품을 만든다.
     *
     * <p>피드는 READY 상태만 노출한다. 이 테스트의 관심사는 정렬과 커서라, Worker 콜백 왕복을 재현하는
     * 대신 상태만 직접 바꾼다(콜백 경로 자체는 {@code ArtworkModuleTests}가 검증한다).
     */
    private String publishReady(String authorId, ArtworkField field, String imageKey) {
        ArtworkInfo uploaded = artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                List.of(imageKey), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "정렬 검증 작품", "설명", field, CreativeType.ORIGINAL,
                List.of(), List.of(), List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
        jdbcTemplate.update("UPDATE artworks SET status = 'READY' WHERE id = ?", uploaded.id());
        return uploaded.id();
    }

    // 등록일 동률을 만든다 — 드라이버 변환을 읽기·쓰기 양쪽에 똑같이 태워 마이크로초까지 그대로 옮긴다.
    private void copyCreatedAt(String fromArtworkId, String toArtworkId) {
        Timestamp createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM artworks WHERE id = ?", Timestamp.class, fromArtworkId);
        jdbcTemplate.update("UPDATE artworks SET created_at = ? WHERE id = ?", createdAt, toArtworkId);
    }

    private Timestamp createdAtOf(String artworkId) {
        return jdbcTemplate.queryForObject(
                "SELECT created_at FROM artworks WHERE id = ?", Timestamp.class, artworkId);
    }

    private long viewCountOf(String artworkId) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM artworks WHERE id = ?", Long.class, artworkId);
    }

    private long bookmarkCountOf(String artworkId) {
        return jdbcTemplate.queryForObject(
                "SELECT bookmark_count FROM artworks WHERE id = ?", Long.class, artworkId);
    }

    private String registerMember() {
        return memberService.register(
                "sort-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "sort" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "정렬작가").id();
    }
}
