package com.atcrew.artwork.internal.application;

import com.atcrew.media.internal.application.MediaKeySigner;
import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 매시간 핫 점수 배치(PA-04)와 이번 주 가장 핫한 작품 조회(PA-05) 검증 — 홈-R03·R14.
 *
 * <p>핫 작품 조회는 분야 필터 없이 피드 전체를 보므로, 테스트마다 앞 테스트의 작품을 비공개로 돌리고
 * 이벤트·점수표를 비워 서로 섞이지 않게 한다(클래스 안에서는 DB가 공유된다 — {@link DatabaseCleanupExtension}).
 * 기간 조회수는 배치의 입력인 유효 열람 이벤트를 직접 넣어 만든다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class HotArtworkTests {

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    HotScoreScheduler hotScoreScheduler;

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // DATETIME(6) 정밀도에 맞춘다 — 나노초가 남으면 경계값 비교가 저장 과정의 절삭 때문에 흔들린다.
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @BeforeEach
    void isolate() {
        jdbcTemplate.update("UPDATE artworks SET visibility = 'PRIVATE'");
        jdbcTemplate.update("DELETE FROM artwork_view_events");
        jdbcTemplate.update("DELETE FROM artwork_hot_scores");
    }

    // ─── PA-04 매시간 배치 ─────────────────────────────────────────

    @Test
    void 기간_조회수는_최근_168시간_이벤트만_센다() {
        String artworkId = publishReady();
        Instant windowStart = now.minus(Duration.ofHours(168));
        insertEvent(artworkId, windowStart);                          // 경계 시각 — 포함
        insertEvent(artworkId, windowStart.plus(Duration.ofHours(1)));
        insertEvent(artworkId, now.minusSeconds(1));
        insertEvent(artworkId, windowStart.minus(1, ChronoUnit.MICROS)); // 경계 직전 — 제외
        insertEvent(artworkId, now.minus(Duration.ofDays(30)));
        String staleOnly = publishReady();
        insertEvent(staleOnly, windowStart.minusSeconds(1));

        hotScoreScheduler.recompute(now);

        assertThat(windowViews()).containsExactly(Map.entry(artworkId, 3L));
    }

    @Test
    void 재계산은_몇_번을_돌려도_같은_결과다() {
        String first = publishReady();
        String second = publishReady();
        insertEvents(first, 3, now.minus(Duration.ofHours(2)));
        insertEvents(second, 1, now.minus(Duration.ofHours(100)));

        hotScoreScheduler.recompute(now);
        Map<String, Long> once = windowViews();
        hotScoreScheduler.recompute(now);

        assertThat(windowViews()).isEqualTo(once).containsOnly(Map.entry(first, 3L), Map.entry(second, 1L));
    }

    @Test
    void 창을_벗어난_작품의_점수는_다음_재계산에서_사라진다() {
        String artworkId = publishReady();
        insertEvent(artworkId, now.minus(Duration.ofHours(167)));
        hotScoreScheduler.recompute(now);
        assertThat(windowViews()).containsOnlyKeys(artworkId);

        hotScoreScheduler.recompute(now.plus(Duration.ofHours(2)));

        assertThat(windowViews()).isEmpty();
    }

    // ─── PA-05 핫 작품 조회 ────────────────────────────────────────

    @Test
    void 기간_조회수_북마크수_등록일_ID_순으로_정렬한다() {
        String mostViewed = publishReady();
        String moreBookmarked = publishReady();
        String newest = publishReady();
        String middle = publishReady();
        String tiedA = publishReady();
        String tiedB = publishReady();
        insertEvents(mostViewed, 5, now.minusSeconds(60));
        for (String id : List.of(moreBookmarked, newest, middle, tiedA, tiedB)) {
            insertEvents(id, 3, now.minusSeconds(60));
        }
        setBookmarkCount(mostViewed, 0);
        setBookmarkCount(moreBookmarked, 2);
        for (String id : List.of(newest, middle, tiedA, tiedB)) {
            setBookmarkCount(id, 1);
        }
        setCreatedAt(newest, now.minus(Duration.ofDays(1)));
        setCreatedAt(middle, now.minus(Duration.ofDays(2)));
        setCreatedAt(tiedA, now.minus(Duration.ofDays(3)));
        setCreatedAt(tiedB, now.minus(Duration.ofDays(3)));
        hotScoreScheduler.recompute(now);

        List<String> tiedByIdAsc = List.of(tiedA, tiedB).stream().sorted().toList();
        assertThat(hotIds()).containsExactly(
                mostViewed, moreBookmarked, newest, middle, tiedByIdAsc.get(0), tiedByIdAsc.get(1));
    }

    @Test
    void 조회수_있는_후보가_6개_미만이면_조회수_0_후보로_같은_순서를_따라_채운다() {
        String viewedLess = publishReady();
        String viewedMore = publishReady();
        insertEvents(viewedLess, 1, now.minusSeconds(60));
        insertEvents(viewedMore, 2, now.minusSeconds(60));
        // 조회수 0 후보 — 북마크가 많아도 조회수 있는 후보보다 앞서지 않는다.
        String zeroTopBookmark = publishReady();
        String zeroNewer = publishReady();
        String zeroOlder = publishReady();
        String zeroOldest = publishReady();
        String zeroLeftOut = publishReady();
        setBookmarkCount(zeroTopBookmark, 10);
        setCreatedAt(zeroNewer, now.minus(Duration.ofDays(1)));
        setCreatedAt(zeroOlder, now.minus(Duration.ofDays(2)));
        setCreatedAt(zeroOldest, now.minus(Duration.ofDays(3)));
        setCreatedAt(zeroLeftOut, now.minus(Duration.ofDays(4)));
        hotScoreScheduler.recompute(now);

        assertThat(hotIds()).containsExactly(
                viewedMore, viewedLess, zeroTopBookmark, zeroNewer, zeroOlder, zeroOldest);
    }

    @Test
    void 최대_6개까지만_돌려준다() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String id = publishReady();
            insertEvents(id, i + 1, now.minusSeconds(60));
            ids.add(id);
        }
        hotScoreScheduler.recompute(now);

        assertThat(hotIds()).containsExactlyElementsOf(ids.reversed().subList(0, 6));
    }

    @Test
    void 피드_노출_조건을_통과하지_못한_작품은_조회수가_있어도_채움으로도_나오지_않는다() {
        String visible = publishReady();
        String japaneseOnly = publishReady();
        String adult = publishReady();
        String privateOne = publishReady();
        String blocked = publishReady();
        jdbcTemplate.update("UPDATE artwork_languages SET value = 'JA' WHERE artwork_id = ?", japaneseOnly);
        jdbcTemplate.update("UPDATE artworks SET age_rating = 'R18' WHERE id = ?", adult);
        jdbcTemplate.update("UPDATE artworks SET visibility = 'PRIVATE' WHERE id = ?", privateOne);
        jdbcTemplate.update("UPDATE artworks SET blocked_at = ? WHERE id = ?", Timestamp.from(now), blocked);
        for (String id : List.of(visible, japaneseOnly, adult, privateOne, blocked)) {
            insertEvents(id, 5, now.minusSeconds(60));
        }
        // 필터 미통과 작품이 조회수 0이어도 채움 후보가 되지 않는지 함께 본다.
        String zeroJapanese = publishReady();
        jdbcTemplate.update("UPDATE artwork_languages SET value = 'JA' WHERE artwork_id = ?", zeroJapanese);
        hotScoreScheduler.recompute(now);

        List<String> hot = artworkService.getHotArtworks(List.of(Language.KO), null, false).stream()
                .map(ArtworkSummaryInfo::id).toList();

        assertThat(hot).containsExactly(visible);
    }

    @Test
    void 성인_콘텐츠_표시가_켜져_있으면_성인_작품도_후보다() {
        String adult = publishReady();
        jdbcTemplate.update("UPDATE artworks SET age_rating = 'R18' WHERE id = ?", adult);
        insertEvents(adult, 1, now.minusSeconds(60));
        hotScoreScheduler.recompute(now);

        assertThat(artworkService.getHotArtworks(List.of(), null, true))
                .extracting(ArtworkSummaryInfo::id).containsExactly(adult);
        assertThat(artworkService.getHotArtworks(List.of(), null, false)).isEmpty();
    }

    @Test
    void 여러_언어가_일치하는_작품도_한_번만_나온다() {
        String bilingual = publishReady();
        jdbcTemplate.update("INSERT INTO artwork_languages (artwork_id, value) VALUES (?, 'JA')", bilingual);
        insertEvents(bilingual, 1, now.minusSeconds(60));
        String zeroBilingual = publishReady();
        jdbcTemplate.update("INSERT INTO artwork_languages (artwork_id, value) VALUES (?, 'JA')", zeroBilingual);
        hotScoreScheduler.recompute(now);

        List<String> hot = artworkService.getHotArtworks(List.of(Language.KO, Language.JA), null, true).stream()
                .map(ArtworkSummaryInfo::id).toList();

        assertThat(hot).containsExactly(bilingual, zeroBilingual);
    }

    private List<String> hotIds() {
        return artworkService.getHotArtworks(List.of(), null, true).stream().map(ArtworkSummaryInfo::id).toList();
    }

    private Map<String, Long> windowViews() {
        Map<String, Long> scores = new java.util.LinkedHashMap<>();
        jdbcTemplate.query("SELECT artwork_id, window_views FROM artwork_hot_scores ORDER BY artwork_id",
                rs -> { scores.put(rs.getString(1), rs.getLong(2)); });
        return scores;
    }

    private void insertEvents(String artworkId, int count, Instant viewedAt) {
        for (int i = 0; i < count; i++) {
            insertEvent(artworkId, viewedAt);
        }
    }

    private void insertEvent(String artworkId, Instant viewedAt) {
        jdbcTemplate.update("""
                INSERT INTO artwork_view_events (id, artwork_id, viewer_type, viewer_key, view_kind, viewed_at)
                VALUES (?, ?, 'ANONYMOUS', ?, 'FIRST', ?)
                """, UUID.randomUUID().toString(), artworkId, UUID.randomUUID().toString(), Timestamp.from(viewedAt));
    }

    private void setBookmarkCount(String artworkId, long count) {
        jdbcTemplate.update("UPDATE artworks SET bookmark_count = ? WHERE id = ?", count, artworkId);
    }

    private void setCreatedAt(String artworkId, Instant createdAt) {
        jdbcTemplate.update("UPDATE artworks SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), artworkId);
    }

    /** 스타터 작품 상한(4개)에 걸리지 않도록 작품마다 작가를 새로 만든다. 업로드 순서가 곧 등록일 순서다. */
    private String publishReady() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String authorId = memberService.register("hot-" + suffix + "@atcrew.com", "hot" + suffix, "핫작가").id();
        ArtworkInfo uploaded = artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                List.of(signedKey(authorId, "hot-" + suffix)), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "핫 작품 검증", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
        jdbcTemplate.update("UPDATE artworks SET status = 'READY' WHERE id = ?", uploaded.id());
        return uploaded.id();
    }

    /**
     * 그 회원에게 발급된 것과 같은 형태의 업로드 key(#190) — 소유 검증이 서명만 보므로 presign을 부르지 않고
     * 같은 규칙으로 만든다.
     */
    private String signedKey(String memberId, String name) {
        return "raw/" + keySigner.sign(memberId, name) + "/" + name + ".png";
    }

}
