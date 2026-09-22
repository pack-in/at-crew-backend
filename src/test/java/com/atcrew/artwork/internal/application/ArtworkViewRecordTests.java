package com.atcrew.artwork.internal.application;

import com.atcrew.media.internal.application.MediaKeySigner;
import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 작품 열람 기록(PA-03)과 탈퇴 회원 비식별화(PA-09) 검증 — 홈-R14 24시간 dedup.
 *
 * <p>테스트마다 새 작품을 만들고 그 작품 ID로만 세므로 클래스 안에서 DB를 공유해도 서로 섞이지 않는다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class ArtworkViewRecordTests {

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 같은_회원의_24시간_이내_반복_열람은_한_번만_센다() {
        String artworkId = publishReady(registerMember());
        String viewer = registerMember();

        artworkService.recordView(artworkId, viewer, null);
        artworkService.recordView(artworkId, viewer, null);
        artworkService.recordView(artworkId, viewer, null);

        assertThat(eventKindsOf(artworkId)).containsExactly("FIRST");
        assertThat(viewCountOf(artworkId)).isEqualTo(1L);
    }

    @Test
    void 직전_집계로부터_24시간이_지나면_재방문으로_다시_센다() {
        String artworkId = publishReady(registerMember());
        String viewer = registerMember();
        artworkService.recordView(artworkId, viewer, null);

        // 24시간이 지난 상태를 만든다 — 판정 기준은 dedup 행의 마지막 집계 시각이다.
        jdbcTemplate.update("UPDATE artwork_view_dedup SET last_counted_at = last_counted_at - INTERVAL 25 HOUR"
                + " WHERE artwork_id = ?", artworkId);
        artworkService.recordView(artworkId, viewer, null);
        // 재방문 직후 반복 열람은 다시 24시간 창 안이다.
        artworkService.recordView(artworkId, viewer, null);

        assertThat(eventKindsOf(artworkId)).containsExactly("FIRST", "REVISIT");
        assertThat(viewCountOf(artworkId)).isEqualTo(2L);
    }

    @Test
    void 이십사시간이_아직_안_지났으면_재방문으로_세지_않는다() {
        String artworkId = publishReady(registerMember());
        String viewer = registerMember();
        artworkService.recordView(artworkId, viewer, null);

        jdbcTemplate.update("UPDATE artwork_view_dedup SET last_counted_at = last_counted_at - INTERVAL 23 HOUR"
                + " WHERE artwork_id = ?", artworkId);
        artworkService.recordView(artworkId, viewer, null);

        assertThat(eventKindsOf(artworkId)).containsExactly("FIRST");
    }

    @Test
    void 같은_열람자의_동시_요청은_한_번만_센다() throws Exception {
        String artworkId = publishReady(registerMember());
        String anonymousId = UUID.randomUUID().toString();

        runConcurrently(8, () -> artworkService.recordView(artworkId, null, anonymousId));

        assertThat(eventKindsOf(artworkId)).containsExactly("FIRST");
        assertThat(viewCountOf(artworkId)).isEqualTo(1L);
    }

    @Test
    void 서로_다른_열람자의_동시_최초_열람은_모두_센다() throws Exception {
        // 기본 격리 수준(REPEATABLE READ)에서는 없는 행에 대한 조건부 UPDATE의 갭 락끼리 교착돼 일부가 실패했다.
        String artworkId = publishReady(registerMember());

        runConcurrently(8, () -> artworkService.recordView(artworkId, null, UUID.randomUUID().toString()));

        assertThat(eventKindsOf(artworkId)).hasSize(8);
        assertThat(viewCountOf(artworkId)).isEqualTo(8L);
    }

    @Test
    void 본인_작품_열람은_세지_않는다() {
        String author = registerMember();
        String artworkId = publishReady(author);

        artworkService.recordView(artworkId, author, null);

        assertThat(eventKindsOf(artworkId)).isEmpty();
        assertThat(viewCountOf(artworkId)).isZero();
    }

    @Test
    void 열람할_수_없는_작품과_없는_작품은_조용히_무시한다() {
        String artworkId = publishReady(registerMember());
        jdbcTemplate.update("UPDATE artworks SET visibility = 'PRIVATE' WHERE id = ?", artworkId);

        artworkService.recordView(artworkId, registerMember(), null);
        artworkService.recordView(UUID.randomUUID().toString(), registerMember(), null);

        assertThat(eventKindsOf(artworkId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM artwork_view_dedup WHERE artwork_id = ?",
                Long.class, artworkId)).isZero();
    }

    @Test
    void 회원과_익명_기록은_합치지_않는다() {
        String artworkId = publishReady(registerMember());
        String viewer = registerMember();
        String anonymousId = UUID.randomUUID().toString();

        // 같은 사람이 로그인 전에 보고 로그인 후 다시 본 상황 — 서로 다른 열람자로 각각 센다.
        artworkService.recordView(artworkId, null, anonymousId);
        artworkService.recordView(artworkId, viewer, anonymousId);

        assertThat(jdbcTemplate.queryForList(
                "SELECT viewer_type FROM artwork_view_events WHERE artwork_id = ? ORDER BY viewer_type",
                String.class, artworkId)).containsExactly("ANONYMOUS", "MEMBER");
        assertThat(viewCountOf(artworkId)).isEqualTo(2L);
    }

    @Test
    void 회원이면_익명_헤더는_형식이_틀려도_보지_않는다() {
        String artworkId = publishReady(registerMember());
        String viewer = registerMember();

        artworkService.recordView(artworkId, viewer, "not-a-uuid");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT viewer_key FROM artwork_view_events WHERE artwork_id = ?", String.class, artworkId))
                .isEqualTo(viewer);
    }

    @Test
    void 비로그인_요청의_익명_ID가_UUID_형식이_아니면_거부한다() {
        String artworkId = publishReady(registerMember());

        for (String invalid : List.of("not-a-uuid", "", "1-1-1-1-1", UUID.randomUUID() + "0")) {
            assertThatThrownBy(() -> artworkService.recordView(artworkId, null, invalid))
                    .isInstanceOf(ArtworkException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_ANONYMOUS_ID");
        }
        assertThat(eventKindsOf(artworkId)).isEmpty();
    }

    @Test
    void 대소문자만_다른_익명_ID는_같은_열람자다() {
        String artworkId = publishReady(registerMember());
        String anonymousId = UUID.randomUUID().toString();

        artworkService.recordView(artworkId, null, anonymousId);
        artworkService.recordView(artworkId, null, anonymousId.toUpperCase());

        assertThat(eventKindsOf(artworkId)).containsExactly("FIRST");
    }

    @Test
    void 식별값이_없는_열람은_기록하지_않는다() {
        String artworkId = publishReady(registerMember());

        artworkService.recordView(artworkId, null, null);

        assertThat(eventKindsOf(artworkId)).isEmpty();
        assertThat(viewCountOf(artworkId)).isZero();
    }

    @Test
    void 작품_상세_조회는_조회수를_올리지_않는다() {
        String author = registerMember();
        String artworkId = publishReady(author);

        artworkService.getArtwork(artworkId, author);
        artworkService.getArtwork(artworkId, registerMember());
        artworkService.getArtwork(artworkId, null);

        assertThat(viewCountOf(artworkId)).isZero();
        assertThat(eventKindsOf(artworkId)).isEmpty();
    }

    @Test
    void 탈퇴하면_열람_기록은_남기고_회원_식별값만_지운다() {
        String artworkId = publishReady(registerMember());
        String leaving = registerMember();
        String staying = registerMember();
        String otherArtworkId = publishReady(registerMember());
        artworkService.recordView(artworkId, leaving, null);
        artworkService.recordView(otherArtworkId, leaving, null);
        artworkService.recordView(artworkId, staying, null);

        memberService.deactivate(leaving);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artwork_view_events WHERE artwork_id IN (?, ?)",
                Long.class, artworkId, otherArtworkId)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artwork_view_events WHERE viewer_key = ?", Long.class, leaving)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artwork_view_events WHERE artwork_id IN (?, ?) AND viewer_key IS NULL",
                Long.class, artworkId, otherArtworkId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artwork_view_dedup WHERE viewer_key = ?", Long.class, leaving)).isZero();
        // 다른 회원의 기록과 누적 조회수는 그대로다.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artwork_view_dedup WHERE viewer_key = ?", Long.class, staying)).isEqualTo(1L);
        assertThat(viewCountOf(artworkId)).isEqualTo(2L);
        assertThat(viewCountOf(otherArtworkId)).isEqualTo(1L);
    }

    @Test
    void 탈퇴한_회원의_남은_토큰으로_열람해도_기록하지_않는다() {
        String artworkId = publishReady(registerMember());
        String leaving = registerMember();
        memberService.deactivate(leaving);

        // 액세스 토큰은 탈퇴 후에도 만료 전까지 유효해 컨트롤러가 회원 ID를 그대로 넘긴다.
        artworkService.recordView(artworkId, leaving, UUID.randomUUID().toString());

        assertThat(eventKindsOf(artworkId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artwork_view_dedup WHERE viewer_key = ?", Long.class, leaving)).isZero();
        assertThat(viewCountOf(artworkId)).isZero();
    }

    private void runConcurrently(int threads, Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = java.util.stream.IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        startSignal.await();
                        action.run();
                        return null;
                    }))
                    .toList();
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get(); // 교착·PK 충돌 예외가 있었다면 여기서 드러난다
            }
        } finally {
            pool.shutdown();
        }
    }

    private List<String> eventKindsOf(String artworkId) {
        return jdbcTemplate.queryForList(
                "SELECT view_kind FROM artwork_view_events WHERE artwork_id = ? ORDER BY viewed_at, id",
                String.class, artworkId);
    }

    private long viewCountOf(String artworkId) {
        return jdbcTemplate.queryForObject("SELECT view_count FROM artworks WHERE id = ?", Long.class, artworkId);
    }

    private String publishReady(String authorId) {
        ArtworkInfo uploaded = artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                List.of(signedKey(authorId, "view-" + UUID.randomUUID())), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "열람 검증 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
        jdbcTemplate.update("UPDATE artworks SET status = 'READY' WHERE id = ?", uploaded.id());
        return uploaded.id();
    }

    private String registerMember() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return memberService.register("view-" + suffix + "@atcrew.com", "view" + suffix, "열람작가").id();
    }

    /**
     * 그 회원에게 발급된 것과 같은 형태의 업로드 key(#190) — 소유 검증이 서명만 보므로 presign을 부르지 않고
     * 같은 규칙으로 만든다.
     */
    private String signedKey(String memberId, String name) {
        return "raw/" + keySigner.sign(memberId, name) + "/" + name + ".png";
    }

}
