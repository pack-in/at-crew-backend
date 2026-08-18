package com.atcrew.artwork;

import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberService;
import com.atcrew.support.BillingTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * artwork 모듈 통합 검증.
 *
 * <p>MariaDB 전환 — MongoDB(@Document) → JPA(@Entity) 전환 후 자식 엔티티(images/materials) 매핑과
 * 이미지·자재 교체 시의 2단계 flush 패턴(§3.3.2 계열, 신규 발견)을 검증한다.
 *
 * <p>media 모듈 추출(docs/design/media-module-design.md §9.1-9) — 업로드/수정/삭제/복구 흐름이
 * media 위임 이후에도 그대로 동작하는지, 그리고 webhook 콜백 → {@code MediaAssetProcessedEvent} →
 * artwork 리스너 상태 갱신 경로가 이어지는지를 검증한다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Testcontainers
class ArtworkModuleTests {

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    // Worker webhook이 도달하는 지점 — 여기서 MediaAssetProcessedEvent가 발행되고 artwork 리스너가 이를 소비한다.
    @Autowired
    MediaCallbackService mediaCallbackService;

    @Test
    void 작품_업로드_후_모든_필드가_그대로_조회된다() {
        String memberId = registerAuthor();

        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of("raw/1.png", "raw/2.png"), 1, "raw/thumb.png", ImageLayoutType.VERTICAL_SCROLL,
                "제목", "설명", ArtworkField.WEBTOON, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART, ArtworkRole.COLORING), List.of(Genre.FANTASY, Genre.ACTION), List.of("태그1", "태그2"),
                AgeRating.ALL, Visibility.PUBLIC, List.of("clip studio"),
                new WorkDuration(1, 2, 3, 4), 12, List.of("https://youtube.com/watch?v=1"),
                List.of(new MaterialData("배경소스", List.of(MaterialTarget.BACKGROUND), List.of("raw/mat.png"), List.of("https://acon3d.com/x")))
        ));

        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), memberId);

        assertThat(found.title()).isEqualTo("제목");
        assertThat(found.images()).hasSize(2);
        assertThat(found.representativeImageIndex()).isEqualTo(1);
        assertThat(found.roles()).containsExactlyInAnyOrder(ArtworkRole.LINEART, ArtworkRole.COLORING);
        assertThat(found.genres()).containsExactlyInAnyOrder(Genre.FANTASY, Genre.ACTION);
        assertThat(found.tags()).containsExactlyInAnyOrder("태그1", "태그2");
        assertThat(found.tools()).containsExactly("clip studio");
        assertThat(found.workDuration()).isEqualTo(new WorkDuration(1, 2, 3, 4));
        assertThat(found.cutCount()).isEqualTo(12);
        assertThat(found.videoLinks()).containsExactly("https://youtube.com/watch?v=1");
        assertThat(found.materials()).hasSize(1);
        assertThat(found.materials().get(0).name()).isEqualTo("배경소스");
        // 소재 대상은 JSON 컬럼에 enum 이름 배열로 저장된다 — 왕복 매핑까지 검증한다.
        assertThat(found.materials().get(0).targets()).containsExactly(MaterialTarget.BACKGROUND);
        assertThat(found.materials().get(0).attachmentKeys()).containsExactly("raw/mat.png");
        assertThat(found.status()).isEqualTo(ArtworkStatus.PROCESSING);
    }

    @Test
    void 이미지_교체_후_기존_이미지_행이_삭제되고_새_이미지로_대체된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, "raw/old1.png", "raw/old2.png");

        ArtworkInfo updated = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                List.of("raw/new1.png", "raw/new2.png", "raw/new3.png"), 2, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(updated.images()).extracting(ArtworkImageInfo::originalKey)
                .containsExactly("raw/new1.png", "raw/new2.png", "raw/new3.png");
        assertThat(updated.representativeImageIndex()).isEqualTo(2);
    }

    @Test
    void 자재_교체_후_기존_자재_행이_삭제되고_새_자재로_대체된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, baseUploadCommand(
                List.of("raw/1.png"),
                List.of(new MaterialData("옛소재", List.of(MaterialTarget.BACKGROUND), List.of(), List.of()))));

        ArtworkInfo updated = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(new MaterialData("새소재1", List.of(MaterialTarget.CHARACTER), List.of(), List.of()),
                        new MaterialData("새소재2", List.of(MaterialTarget.ACCESSORY), List.of(), List.of()))));

        assertThat(updated.materials()).extracting(MaterialInfo::name)
                .containsExactly("새소재1", "새소재2");
    }

    @Test
    void 반복적인_이미지_교체도_유니크_제약_충돌_없이_동작한다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, "raw/a.png");

        for (int i = 0; i < 3; i++) {
            uploaded = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                    List.of("raw/round" + i + "-1.png", "raw/round" + i + "-2.png"), 0, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null));
        }

        assertThat(uploaded.images()).hasSize(2);
    }

    @Test
    void 이미지_처리_콜백이_media를_거쳐_작품_상태와_변환결과에_반영된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, "raw/c1.png");

        processImage(uploaded.id(), "raw/c1.png", MediaProcessingStatus.DONE);

        awaitReady(memberId, uploaded.id());
        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(found.images()).hasSize(1);
        assertThat(found.images().get(0).thumbKey()).isEqualTo("thumb/c1.avif");
        assertThat(found.images().get(0).originalAvifKey()).isEqualTo("original/c1.avif");
        assertThat(found.images().get(0).processingStatus()).isEqualTo(ImageProcessingStatus.DONE);
    }

    @Test
    void 이미지_일부가_실패해도_나머지가_성공하면_READY로_전환된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, "raw/p1.png", "raw/p2.png");

        processImage(uploaded.id(), "raw/p1.png", MediaProcessingStatus.DONE);
        processImage(uploaded.id(), "raw/p2.png", MediaProcessingStatus.FAILED);

        awaitReady(memberId, uploaded.id());
        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(found.images()).extracting(ArtworkImageInfo::processingStatus)
                .containsExactly(ImageProcessingStatus.DONE, ImageProcessingStatus.FAILED);
    }

    // 동시성 시맨틱 검증 (docs/design/mariadb-migration-design.md §7 리스크 3 — 스레드 2개 경합).
    // 같은 작품의 이미지 처리완료 이벤트가 동시에 도착하면 두 리스너 트랜잭션이 겹친다 —
    // ArtworkMediaEventListener가 부모 작품 행을 비관적 락으로 직렬화하지 않으면 서로의 갱신을
    // 보지 못한 채 READY 판정을 놓쳐 작품이 PROCESSING에 갇힌다.
    @Test
    void 같은_작품의_이미지_이벤트가_동시에_도착해도_READY로_전환된다() throws Exception {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, "raw/r1.png", "raw/r2.png");

        processConcurrently(
                () -> processImage(uploaded.id(), "raw/r1.png", MediaProcessingStatus.DONE),
                () -> processImage(uploaded.id(), "raw/r2.png", MediaProcessingStatus.DONE));

        awaitReady(memberId, uploaded.id());
        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(found.images()).extracting(ArtworkImageInfo::processingStatus)
                .containsExactly(ImageProcessingStatus.DONE, ImageProcessingStatus.DONE);
    }

    @Test
    void 삭제_후_복원하면_이전_공개범위로_돌아온다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, "raw/x.png");
        processImage(uploaded.id(), "raw/x.png", MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());

        artworkService.deleteArtwork(memberId, uploaded.id());
        artworkService.restoreArtworks(memberId, List.of(uploaded.id()));

        ArtworkInfo restored = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(restored.status()).isEqualTo(ArtworkStatus.READY);
        assertThat(restored.visibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void 영구삭제하면_다시_조회되지_않는다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, "raw/y.png");
        // 이미지 처리 완료 상태로 만들어 둔다 — permanentlyDeleteArtworks의 allKeys 조합에 List.of()를 쓰는
        // 기존(Mongo 시절부터의) 로직은 처리되지 않은 null 키가 섞이면 NPE가 나는 사전 존재 결함이라
        // 이번 마이그레이션 범위 밖으로 두고, 테스트에서는 트리거하지 않도록 우회한다.
        processImage(uploaded.id(), "raw/y.png", MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());
        artworkService.deleteArtwork(memberId, uploaded.id());

        artworkService.permanentlyDeleteArtworks(memberId, List.of(uploaded.id()));

        assertThatThrownBy(() -> artworkService.getArtwork(uploaded.id(), memberId))
                .isInstanceOf(RuntimeException.class);
    }

    /** Worker webhook 1건을 재현한다 — media가 자산 상태를 갱신하고 MediaAssetProcessedEvent를 발행한다. */
    private void processImage(String artworkId, String imageKey, MediaProcessingStatus status) {
        boolean done = status == MediaProcessingStatus.DONE;
        String name = imageKey.substring(imageKey.indexOf('/') + 1, imageKey.lastIndexOf('.'));
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                done ? "thumb/" + name + ".avif" : null,
                done ? "thumb-adult/" + name + ".avif" : null,
                done ? "original/" + name + ".avif" : null,
                status);
    }

    /** artwork 리스너는 @ApplicationModuleListener(비동기)라 상태 반영까지 폴링한다. */
    private void awaitReady(String memberId, String artworkId) {
        awaitCondition(() -> artworkService.getArtworkStatus(memberId, artworkId) == ArtworkStatus.READY);
    }

    /** 두 webhook 콜백을 같은 순간에 재현해 두 리스너 트랜잭션이 실제로 겹치게 만든다 (§7 리스크 3). */
    private void processConcurrently(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = Stream.of(first, second)
                    .map(action -> pool.submit(() -> {
                        startSignal.await();
                        action.run();
                        return null;
                    }))
                    .toList();
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }
    }

    private void awaitCondition(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("상태 반영 대기 시간 초과");
    }

    @Test
    void 스타터_플랜은_작품을_4개까지만_등록할_수_있다() {
        String memberId = registerAuthor();
        for (int i = 0; i < 4; i++) {
            uploadMinimal(memberId, "raw/starter-" + i + ".png");
        }

        assertThatThrownBy(() -> uploadMinimal(memberId, "raw/starter-5.png"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("STARTER_ARTWORK_LIMIT_EXCEEDED");
    }

    @Test
    void 프로_플랜은_작품_개수_제한이_없고_다운그레이드해도_기존_작품은_유지된다() {
        String memberId = registerAuthor();
        String subscriptionId = BillingTestSupport.grantProPlan(subscriptionRepository, memberId);
        for (int i = 0; i < 6; i++) {
            uploadMinimal(memberId, "raw/pro-" + i + ".png");
        }

        BillingTestSupport.cancelPlan(subscriptionRepository, subscriptionId);

        // 기존 산출물은 유지되고 신규 생성만 막힌다(요금제-R01)
        assertThat(artworkService.getMyArtworks(memberId, null, 20).items()).hasSize(6);
        assertThatThrownBy(() -> uploadMinimal(memberId, "raw/pro-after-downgrade.png"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("STARTER_ARTWORK_LIMIT_EXCEEDED");
    }

    private ArtworkInfo uploadMinimal(String memberId, String... imageKeys) {
        return artworkService.uploadArtwork(memberId, baseUploadCommand(List.of(imageKeys), List.of()));
    }

    private UploadArtworkCommand baseUploadCommand(List<String> imageKeys, List<MaterialData> materials) {
        return new UploadArtworkCommand(
                imageKeys, 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "테스트 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), List.of(),
                AgeRating.ALL, Visibility.PUBLIC, List.of(), null, null, List.of(), materials);
    }

    private String registerAuthor() {
        return memberService.register(
                "artwork-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "aw" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "작가", CreatorRole.ILLUSTRATOR).id();
    }
}
