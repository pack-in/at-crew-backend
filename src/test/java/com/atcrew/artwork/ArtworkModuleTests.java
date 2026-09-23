package com.atcrew.artwork;

import com.atcrew.SharedContainersConfig;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.response.OffsetPage;
import com.atcrew.media.MediaConstraints;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.media.internal.application.MediaKeySigner;
import com.atcrew.member.AuthProvider;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import com.atcrew.member.RegisterMemberCommand;
import com.atcrew.support.BillingTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import com.atcrew.artwork.internal.application.TrashPurgeScheduler;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class ArtworkModuleTests {

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    // Worker webhook이 도달하는 지점 — 여기서 MediaAssetProcessedEvent가 발행되고 artwork 리스너가 이를 소비한다.
    @Autowired
    MediaCallbackService mediaCallbackService;

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

    // 운영 차단은 관리자 API 없이 DB 직접 UPDATE로 이뤄지므로 테스트도 같은 경로를 쓴다.
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TrashPurgeScheduler trashPurgeScheduler;

    @Test
    void 작품_업로드_후_모든_필드가_그대로_조회된다() {
        String memberId = registerAuthor();

        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(signedKey(memberId, "1"), signedKey(memberId, "2")), 1, signedKey(memberId, "thumb"), ImageLayoutType.VERTICAL_SCROLL,
                "제목", "설명", ArtworkField.WEBTOON, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART, ArtworkRole.COLORING), List.of(Genre.FANTASY, Genre.ACTION), null, List.of("태그1", "태그2"),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of("clip studio"),
                new WorkDuration(1, 2, 3, 4), 12, List.of("https://youtube.com/watch?v=1"),
                List.of(new MaterialData("배경소스", List.of(MaterialTarget.BACKGROUND), null, List.of(signedKey(memberId, "mat")), List.of("https://acon3d.com/x")))
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
        assertThat(found.materials().get(0).attachmentKeys()).containsExactly(signedKey(memberId, "mat"));
        assertThat(found.status()).isEqualTo(ArtworkStatus.PROCESSING);
    }

    // 홈-R05 — 작품 태그는 업로드 폼에서 등록한 순서대로 노출한다(이슈 #199).
    @Test
    void 태그는_등록한_순서대로_조회되고_수정하면_새_순서를_따른다() {
        String memberId = registerAuthor();

        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(signedKey(memberId, "1")), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "제목", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART), List.of(Genre.FANTASY), null, List.of("캐릭터", "SF", "캐릭터", "배경"),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(),
                null, null, List.of(), List.of()
        ));

        // 중복은 처음 등록한 위치만 남긴다.
        assertThat(artworkService.getArtwork(uploaded.id(), memberId).tags())
                .containsExactly("캐릭터", "SF", "배경");

        // 같은 값의 순서만 뒤바꾸는 수정 — 행 단위 갱신이면 (artwork_id, value) 충돌이 날 수 있는 경우다.
        artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                null, null, null, null, null, null, null, null,
                null, null, null, List.of("배경", "캐릭터", "SF"), null, null, null, null, null, null, null));
        assertThat(artworkService.getArtwork(uploaded.id(), memberId).tags())
                .containsExactly("배경", "캐릭터", "SF");

        artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                null, null, null, null, null, null, null, null,
                null, null, null, List.of("SF"), null, null, null, null, null, null, null));
        assertThat(artworkService.getArtwork(uploaded.id(), memberId).tags()).containsExactly("SF");
    }

    @Test
    void 업로드_시_담당업무_장르_소재대상_직접입력_값이_저장되고_조회에_반영된다() {
        String memberId = registerAuthor();

        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(signedKey(memberId, "1")), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "제목", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.ETC), List.of(),
                List.of(new ArtworkCustomTagInfo(ArtworkCustomTagType.ROLE, "특수효과"),
                        new ArtworkCustomTagInfo(ArtworkCustomTagType.GENRE, "이세계")),
                List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(),
                null, null, List.of(),
                List.of(new MaterialData("소품", List.of(MaterialTarget.WEAPON),
                        List.of("커스텀무기"), List.of(), List.of()))
        ));

        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), memberId);

        assertThat(found.customTags()).containsExactlyInAnyOrder(
                new ArtworkCustomTagInfo(ArtworkCustomTagType.ROLE, "특수효과"),
                new ArtworkCustomTagInfo(ArtworkCustomTagType.GENRE, "이세계"));
        assertThat(found.materials().get(0).customTargets()).containsExactly("커스텀무기");
    }

    @Test
    void 담당업무_직접입력_값이_10자를_초과하면_예외() {
        String memberId = registerAuthor();

        assertThatThrownBy(() -> artworkService.uploadArtwork(memberId, baseUploadCommandWithCustomTags(memberId,
                List.of(new ArtworkCustomTagInfo(ArtworkCustomTagType.ROLE, "12345678901")))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("INVALID_CUSTOM_TAG");
    }

    @Test
    void 같은_항목_안에서_중복된_직접입력_값은_조용히_무시된다() {
        String memberId = registerAuthor();

        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, baseUploadCommandWithCustomTags(memberId,
                List.of(new ArtworkCustomTagInfo(ArtworkCustomTagType.ROLE, "특수효과"),
                        new ArtworkCustomTagInfo(ArtworkCustomTagType.ROLE, "특수효과"))));

        assertThat(uploaded.customTags()).containsExactly(
                new ArtworkCustomTagInfo(ArtworkCustomTagType.ROLE, "특수효과"));
    }

    @Test
    void 수정_시_customTags가_null이면_유지되고_빈_목록이면_전체_삭제된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, baseUploadCommandWithCustomTags(memberId,
                List.of(new ArtworkCustomTagInfo(ArtworkCustomTagType.ROLE, "특수효과"))));

        ArtworkInfo unchanged = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        assertThat(unchanged.customTags()).containsExactly(
                new ArtworkCustomTagInfo(ArtworkCustomTagType.ROLE, "특수효과"));

        ArtworkInfo cleared = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                null, null, null, null, null, null, null, null, null, null, List.of(),
                null, null, null, null, null, null, null, null));
        assertThat(cleared.customTags()).isEmpty();
    }

    private UploadArtworkCommand baseUploadCommandWithCustomTags(String memberId, List<ArtworkCustomTagInfo> customTags) {
        return new UploadArtworkCommand(
                List.of(signedKey(memberId, "1")), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "테스트 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), customTags, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of());
    }

    @Test
    void 이미지_교체_후_기존_이미지_행이_삭제되고_새_이미지로_대체된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "old1"), signedKey(memberId, "old2"));

        ArtworkInfo updated = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                List.of(signedKey(memberId, "new1"), signedKey(memberId, "new2"), signedKey(memberId, "new3")), 2, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(updated.images()).extracting(ArtworkImageInfo::originalKey)
                .containsExactly(signedKey(memberId, "new1"), signedKey(memberId, "new2"), signedKey(memberId, "new3"));
        assertThat(updated.representativeImageIndex()).isEqualTo(2);
    }

    // 프론트는 수정 요청마다 폼 전체(기존 imageKeys 포함)를 보낸다. 교체로 처리하면 끝난 변환을 버리고
    // 파일을 고아 큐로 넘겨, 이미지를 건드리지 않은 수정만으로 작품 이미지가 전부 깨진다(#193).
    @Test
    void 같은_이미지_목록으로_수정하면_변환_결과를_유지하고_고아_처리하지_않는다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "u1"));
        processImage(uploaded.id(), signedKey(memberId, "u1"), MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());

        ArtworkInfo updated = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                List.of(signedKey(memberId, "u1")), 0, null, null, "새 제목",
                null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(updated.title()).isEqualTo("새 제목");
        assertThat(updated.images()).extracting(ArtworkImageInfo::originalAvifKey).containsExactly("original/u1.avif");
        assertThat(updated.images()).extracting(ArtworkImageInfo::processingStatus)
                .containsExactly(ImageProcessingStatus.DONE);
        assertThat(artworkService.getArtworkStatus(memberId, uploaded.id())).isEqualTo(ArtworkStatus.READY);
        assertThat(orphanedKeys()).doesNotContain(signedKey(memberId, "u1"), "original/u1.avif");
    }

    // #193 — 이미지를 더해도 이미 처리된 이미지는 그대로 둔다. 예전에는 목록이 달라지기만 하면 전량 교체라
    // 남긴 이미지의 파일이 고아 큐로 가 지워지고 재변환이 FAILED가 됐다.
    @Test
    void 이미지를_추가하면_기존_이미지는_변환_결과를_유지하고_고아_처리하지_않는다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "u2"));
        processImage(uploaded.id(), signedKey(memberId, "u2"), MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());
        ArtworkInfo updated = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                List.of(signedKey(memberId, "u2"), signedKey(memberId, "u3")), 0, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(updated.images()).extracting(ArtworkImageInfo::originalKey)
                .containsExactly(signedKey(memberId, "u2"), signedKey(memberId, "u3"));
        assertThat(updated.images().get(0).originalAvifKey()).isEqualTo("original/u2.avif");
        assertThat(updated.images().get(0).processingStatus()).isEqualTo(ImageProcessingStatus.DONE);
        assertThat(updated.images().get(1).processingStatus()).isEqualTo(ImageProcessingStatus.PENDING);
        assertThat(orphanedKeys()).doesNotContain(signedKey(memberId, "u2"), "original/u2.avif");
    }

    // #190 — key는 공개 응답에 그대로 실린다. 검증하지 않으면 남의 key를 지정 썸네일·첨부로 넣고 내 작품을
    // 영구 삭제해 남의 R2 파일을 지울 수 있다.
    @Test
    void 남의_업로드_키로는_작품을_만들_수_없다() {
        String me = registerAuthor();
        String other = registerAuthor();

        assertThatThrownBy(() -> artworkService.uploadArtwork(me, baseUploadCommand(
                List.of(signedKey(other, "stolen")), List.of())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("UNOWNED_IMAGE_KEY");
    }

    @Test
    void 남의_키를_지정_썸네일이나_자료_첨부로도_넣을_수_없다() {
        String me = registerAuthor();
        String other = registerAuthor();

        assertThatThrownBy(() -> artworkService.uploadArtwork(me, new UploadArtworkCommand(
                List.of(signedKey(me, "mine")), 0, signedKey(other, "stolen-thumb"), ImageLayoutType.VERTICAL_SCROLL,
                "제목", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(), AgeRating.ALL, List.of(Language.KO), true, List.of(),
                List.of(), null, null, List.of(), List.of())))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("UNOWNED_IMAGE_KEY");

        assertThatThrownBy(() -> artworkService.uploadArtwork(me, baseUploadCommand(
                List.of(signedKey(me, "mine2")),
                List.of(new MaterialData("소재", List.of(MaterialTarget.BACKGROUND), null,
                        List.of(signedKey(other, "stolen-attachment")), List.of())))))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("UNOWNED_IMAGE_KEY");
    }

    // 변환 결과 key(thumb/…)에는 서명이 없다 — 공개 응답에서 가장 쉽게 얻을 수 있는 값이라 함께 막힌다.
    @Test
    void 변환_결과_키나_옛_형식_키는_제출할_수_없다() {
        String me = registerAuthor();

        assertThatThrownBy(() -> artworkService.uploadArtwork(me, baseUploadCommand(
                List.of("thumb/someone.avif"), List.of())))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("UNOWNED_IMAGE_KEY");
        assertThatThrownBy(() -> artworkService.uploadArtwork(me, baseUploadCommand(
                List.of("raw/legacy-no-signature.png"), List.of())))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("UNOWNED_IMAGE_KEY");
    }

    @Test
    void 이미지를_빼면_빠진_이미지만_고아_처리한다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "u4"), signedKey(memberId, "u5"));
        processImage(uploaded.id(), signedKey(memberId, "u4"), MediaProcessingStatus.DONE);
        processImage(uploaded.id(), signedKey(memberId, "u5"), MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());

        ArtworkInfo updated = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                List.of(signedKey(memberId, "u4")), 0, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(updated.images()).extracting(ArtworkImageInfo::originalKey).containsExactly(signedKey(memberId, "u4"));
        assertThat(updated.images().get(0).originalAvifKey()).isEqualTo("original/u4.avif");
        // 빠진 u5만 고아 큐로 간다 — 남긴 u4는 그대로다.
        assertThat(orphanedKeys()).contains(signedKey(memberId, "u5"), "original/u5.avif")
                .doesNotContain(signedKey(memberId, "u4"), "original/u4.avif");
    }

    @Test
    void 자재_교체_후_기존_자재_행이_삭제되고_새_자재로_대체된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, baseUploadCommand(
                List.of(signedKey(memberId, "1")),
                List.of(new MaterialData("옛소재", List.of(MaterialTarget.BACKGROUND), null, List.of(), List.of()))));

        ArtworkInfo updated = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(new MaterialData("새소재1", List.of(MaterialTarget.CHARACTER), null, List.of(), List.of()),
                        new MaterialData("새소재2", List.of(MaterialTarget.ACCESSORY), null, List.of(), List.of()))));

        assertThat(updated.materials()).extracting(MaterialInfo::name)
                .containsExactly("새소재1", "새소재2");
    }

    @Test
    void 반복적인_이미지_교체도_유니크_제약_충돌_없이_동작한다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "a"));

        for (int i = 0; i < 3; i++) {
            uploaded = artworkService.updateArtwork(memberId, uploaded.id(), new UpdateArtworkCommand(
                    List.of(signedKey(memberId, "round" + i + "-1"), signedKey(memberId, "round" + i + "-2")), 0, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        }

        assertThat(uploaded.images()).hasSize(2);
    }

    @Test
    void 이미지_처리_콜백이_media를_거쳐_작품_상태와_변환결과에_반영된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "c1"));

        processImage(uploaded.id(), signedKey(memberId, "c1"), MediaProcessingStatus.DONE);

        awaitReady(memberId, uploaded.id());
        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(found.images()).hasSize(1);
        assertThat(found.images().get(0).originalAvifKey()).isEqualTo("original/c1.avif");
        // 본문 이미지는 카드 썸네일을 만들지 않는다 — 카드는 사용자 지정 썸네일로만 만든다.
        assertThat(found.images().get(0).thumbKey()).isNull();
        assertThat(found.images().get(0).processingStatus()).isEqualTo(ImageProcessingStatus.DONE);
    }

    @Test
    void 이미지_일부가_실패해도_나머지가_성공하면_READY로_전환된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "p1"), signedKey(memberId, "p2"));

        processImage(uploaded.id(), signedKey(memberId, "p1"), MediaProcessingStatus.DONE);
        processImage(uploaded.id(), signedKey(memberId, "p2"), MediaProcessingStatus.FAILED);

        awaitReady(memberId, uploaded.id());
        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(found.images()).extracting(ArtworkImageInfo::processingStatus)
                .containsExactly(ImageProcessingStatus.DONE, ImageProcessingStatus.FAILED);
    }

    // 전량 실패는 READY 조건("PENDING 없음 AND DONE 하나 이상")의 어느 분기에도 걸리지 않아 PROCESSING에
    // 갇혔었다 — 재시도 스케줄러도 PENDING만 보므로 자력 복구가 불가능했다(2026-09-09 프로덕션 4건).
    @Test
    void 이미지가_전부_실패하면_FAILED로_전환된다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "f1"), signedKey(memberId, "f2"));

        processImage(uploaded.id(), signedKey(memberId, "f1"), MediaProcessingStatus.FAILED);
        processImage(uploaded.id(), signedKey(memberId, "f2"), MediaProcessingStatus.FAILED);

        awaitCondition(() -> artworkService.getArtworkStatus(memberId, uploaded.id()) == ArtworkStatus.FAILED);
        // 실패해도 작성자 본인은 계속 열람할 수 있어야 한다 — 프론트가 재업로드를 안내하려면 상세가 필요하다.
        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(found.images()).extracting(ArtworkImageInfo::processingStatus)
                .containsExactly(ImageProcessingStatus.FAILED, ImageProcessingStatus.FAILED);
    }

    @Test
    void presign은_상한을_넘는_파일_크기를_거부한다() {
        assertThatThrownBy(() -> artworkService.generatePresignedUrls(presignMemberId(), 1, List.of("image/png"),
                List.of(MediaConstraints.MAX_ORIGINAL_BYTES + 1)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("IMAGE_TOO_LARGE");

        // 상한 이하는 그대로 발급되고, fileSizes를 생략한 클라이언트도 계속 받아준다.
        assertThat(artworkService.generatePresignedUrls(presignMemberId(), 1, List.of("image/png"),
                List.of(MediaConstraints.MAX_ORIGINAL_BYTES))).hasSize(1);
        assertThat(artworkService.generatePresignedUrls(presignMemberId(), 1, List.of("image/png"), null)).hasSize(1);
    }

    @Test
    void presign은_count와_fileSizes_수가_다르면_거부한다() {
        assertThatThrownBy(() -> artworkService.generatePresignedUrls(presignMemberId(), 2, List.of("image/png", "image/png"),
                List.of(1024L)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("INVALID_IMAGE_COUNT");
    }

    // 동시성 시맨틱 검증 (docs/design/mariadb-migration-design.md §7 리스크 3 — 스레드 2개 경합).
    // 같은 작품의 이미지 처리완료 이벤트가 동시에 도착하면 두 리스너 트랜잭션이 겹친다 —
    // ArtworkMediaEventListener가 부모 작품 행을 비관적 락으로 직렬화하지 않으면 서로의 갱신을
    // 보지 못한 채 READY 판정을 놓쳐 작품이 PROCESSING에 갇힌다.
    @Test
    void 같은_작품의_이미지_이벤트가_동시에_도착해도_READY로_전환된다() throws Exception {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "r1"), signedKey(memberId, "r2"));

        processConcurrently(
                () -> processImage(uploaded.id(), signedKey(memberId, "r1"), MediaProcessingStatus.DONE),
                () -> processImage(uploaded.id(), signedKey(memberId, "r2"), MediaProcessingStatus.DONE));

        awaitReady(memberId, uploaded.id());
        ArtworkInfo found = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(found.images()).extracting(ArtworkImageInfo::processingStatus)
                .containsExactly(ImageProcessingStatus.DONE, ImageProcessingStatus.DONE);
    }

    @Test
    void 삭제_후_복원하면_이전_공개범위로_돌아온다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "x"));
        processImage(uploaded.id(), signedKey(memberId, "x"), MediaProcessingStatus.DONE);
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
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "y"));
        // 이미지 처리 완료 상태로 만들어 둔다 — 영구 삭제는 처리된 키까지 함께 지우는 경로를 확인해야 한다
        // (처리 전 null 키가 섞여도 NPE가 나던 사전 존재 결함은 PA-20의 allImageKeys 정리로 해소됐다).
        processImage(uploaded.id(), signedKey(memberId, "y"), MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());
        artworkService.deleteArtwork(memberId, uploaded.id());

        artworkService.permanentlyDeleteArtworks(memberId, List.of(uploaded.id()));

        assertThatThrownBy(() -> artworkService.getArtwork(uploaded.id(), memberId))
                .isInstanceOf(RuntimeException.class);
    }

    // 사용자 지정 썸네일 key는 media_assets에 행이 없어 삭제 대상 목록에서 빠지면 어디서도 지워지지
    // 않는다. 더 중요한 것은 고정형 스냅샷 보존 판정이 이 key로 스냅샷을 찾는다는 점이다 —
    // 후보에 없으면 스냅샷이 매칭되지 않아 상세 이미지까지 삭제된다(portfolio-module-design.md §5.6).
    @Test
    void 영구삭제_이벤트는_사용자_지정_썸네일_키까지_담는다(PublishedEvents events) {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(signedKey(memberId, "ct")), 0, signedKey(memberId, "custom-thumb"), ImageLayoutType.VERTICAL_SCROLL,
                "테스트 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
        processImage(uploaded.id(), signedKey(memberId, "ct"), MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());
        artworkService.deleteArtwork(memberId, uploaded.id());

        artworkService.permanentlyDeleteArtworks(memberId, List.of(uploaded.id()));

        assertThat(deletedImageKeysOf(events, uploaded.id()))
                .contains(signedKey(memberId, "ct"), signedKey(memberId, "custom-thumb"));
    }

    // 본문 이미지와 사용자 지정 썸네일은 Worker에 다른 변형본을 요청한다 — 본문에 3:4 썸네일을 만들지 않는다.
    @Test
    void 등록하면_본문은_원본만_썸네일은_썸네일과_블러본을_요청한다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadWithThumbnail(memberId, signedKey(memberId, "p1"), signedKey(memberId, "p1-thumb"));

        assertThat(jdbcTemplate.queryForList(
                "SELECT CONCAT(owner_type, ':', variant_profile) FROM media_assets WHERE owner_id = ? ORDER BY owner_type",
                String.class, uploaded.id()))
                .containsExactly("ARTWORK:ORIGINAL", "ARTWORK_THUMBNAIL:THUMBNAIL_WITH_ADULT_BLUR");
        assertThat(uploaded.images()).extracting(ArtworkImageInfo::originalKey).containsExactly(signedKey(memberId, "p1"));
        assertThat(uploaded.thumbnailImage().originalKey()).isEqualTo(signedKey(memberId, "p1-thumb"));
        assertThat(uploaded.thumbnailImage().processingStatus()).isEqualTo(ImageProcessingStatus.PENDING);
    }

    // 홈-R06 — 성인물 카드는 블러본이 있어야 한다. 예전에는 지정 썸네일이 변환되지 않아 블러본이 항상 없었다.
    @Test
    void 썸네일_변환_전에는_원본을_변환_후에는_썸네일과_블러본을_카드에_쓴다() {
        String memberId = registerAuthor();
        String thumbnailKey = signedKey(memberId, "c-thumb");
        ArtworkInfo uploaded = uploadWithThumbnail(memberId, signedKey(memberId, "c-body"), thumbnailKey);

        ArtworkSummaryInfo pending = artworkService.getMyArtworks(memberId, null, 20).items().get(0);
        assertThat(pending.thumbKey()).isEqualTo(thumbnailKey);
        assertThat(pending.thumbAdultKey()).isNull();

        processThumbnail(uploaded.id(), thumbnailKey);

        ArtworkSummaryInfo card = artworkService.getMyArtworks(memberId, null, 20).items().get(0);
        assertThat(card.thumbKey()).isEqualTo("thumb/c-thumb.avif");
        assertThat(card.thumbAdultKey()).isEqualTo("thumb-adult/c-thumb.avif");
        ArtworkInfo detail = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(detail.thumbnailKey()).isEqualTo(thumbnailKey);
        assertThat(detail.thumbnailImage().thumbKey()).isEqualTo("thumb/c-thumb.avif");
        // 썸네일 처리는 작품 상태를 정하지 않는다 — 본문이 끝나야 READY다.
        assertThat(artworkService.getArtworkStatus(memberId, uploaded.id())).isEqualTo(ArtworkStatus.PROCESSING);
    }

    @Test
    void 썸네일을_바꾸면_이전_썸네일의_원본과_변형본을_고아_처리한다() {
        String memberId = registerAuthor();
        String oldThumb = signedKey(memberId, "r-old");
        ArtworkInfo uploaded = uploadWithThumbnail(memberId, signedKey(memberId, "r-body"), oldThumb);
        processThumbnail(uploaded.id(), oldThumb);

        String newThumb = signedKey(memberId, "r-new");
        ArtworkInfo updated = artworkService.updateArtwork(memberId, uploaded.id(), updateThumbnail(newThumb));

        assertThat(updated.thumbnailKey()).isEqualTo(newThumb);
        assertThat(updated.thumbnailImage().originalKey()).isEqualTo(newThumb);
        assertThat(updated.thumbnailImage().processingStatus()).isEqualTo(ImageProcessingStatus.PENDING);
        assertThat(orphanedKeys()).contains(oldThumb, "thumb/r-old.avif", "thumb-adult/r-old.avif");
    }

    @Test
    void 같은_썸네일로_수정하면_다시_변환하지_않는다() {
        String memberId = registerAuthor();
        String thumb = signedKey(memberId, "s-thumb");
        ArtworkInfo uploaded = uploadWithThumbnail(memberId, signedKey(memberId, "s-body"), thumb);
        processThumbnail(uploaded.id(), thumb);

        ArtworkInfo updated = artworkService.updateArtwork(memberId, uploaded.id(), updateThumbnail(thumb));

        assertThat(updated.thumbnailImage().thumbKey()).isEqualTo("thumb/s-thumb.avif");
        assertThat(orphanedKeys()).doesNotContain(thumb, "thumb/s-thumb.avif");
    }

    // 썸네일 변환 이전에 올라온 작품 — 지정 썸네일이 raw로만 있고 media 자산 행이 없다. 예전에는 교체된 raw가
    // 어디서도 정리되지 않았다. 같은 key로 수정할 때는 새로 변환하지 않는다(변환하면 Worker가 raw를 지워
    // 그 key를 참조하는 고정형 스냅샷이 깨진다).
    @Test
    void 자산_없는_옛_썸네일은_그대로_쓰고_바꿀_때만_raw를_고아_처리한다() {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "l-body"));
        String legacyThumb = signedKey(memberId, "l-old");
        jdbcTemplate.update("UPDATE artworks SET thumbnail_key = ? WHERE id = ?", legacyThumb, uploaded.id());

        ArtworkInfo same = artworkService.updateArtwork(memberId, uploaded.id(), updateThumbnail(legacyThumb));
        assertThat(same.thumbnailImage()).isNull();
        assertThat(artworkService.getMyArtworks(memberId, null, 20).items().get(0).thumbKey()).isEqualTo(legacyThumb);
        assertThat(thumbnailAssetCount(uploaded.id())).isZero();

        artworkService.updateArtwork(memberId, uploaded.id(), updateThumbnail(signedKey(memberId, "l-new")));

        assertThat(orphanedKeys()).contains(legacyThumb);
        assertThat(thumbnailAssetCount(uploaded.id())).isEqualTo(1);
    }

    // 한 raw를 본문과 썸네일로 동시에 변환하면 먼저 끝난 쪽이 raw를 지워 다른 쪽이 FAILED가 된다.
    @Test
    void 썸네일_key가_본문_이미지와_같으면_등록과_수정을_거부한다() {
        String memberId = registerAuthor();
        String shared = signedKey(memberId, "dup");

        assertThatThrownBy(() -> uploadWithThumbnail(memberId, shared, shared))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("THUMBNAIL_KEY_IN_IMAGES");

        ArtworkInfo uploaded = uploadWithThumbnail(memberId, shared, signedKey(memberId, "dup-thumb"));
        assertThatThrownBy(() -> artworkService.updateArtwork(memberId, uploaded.id(), updateThumbnail(shared)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("THUMBNAIL_KEY_IN_IMAGES");
    }

    @Test
    void 영구삭제하면_썸네일_변형본까지_지우고_썸네일_자산_행을_정리한다(PublishedEvents events) {
        String memberId = registerAuthor();
        String thumb = signedKey(memberId, "d-thumb");
        ArtworkInfo uploaded = uploadWithThumbnail(memberId, signedKey(memberId, "d-body"), thumb);
        processImage(uploaded.id(), signedKey(memberId, "d-body"), MediaProcessingStatus.DONE);
        processThumbnail(uploaded.id(), thumb);
        awaitReady(memberId, uploaded.id());
        artworkService.deleteArtwork(memberId, uploaded.id());

        artworkService.permanentlyDeleteArtworks(memberId, List.of(uploaded.id()));

        assertThat(deletedImageKeysOf(events, uploaded.id()))
                .contains(thumb, "thumb/d-thumb.avif", "thumb-adult/d-thumb.avif", "original/d-body.avif");
        awaitCondition(() -> thumbnailAssetCount(uploaded.id()) == 0);
    }

    private ArtworkInfo uploadWithThumbnail(String memberId, String imageKey, String thumbnailKey) {
        return artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(imageKey), 0, thumbnailKey, ImageLayoutType.VERTICAL_SCROLL,
                "썸네일 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.R18, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
    }

    private static UpdateArtworkCommand updateThumbnail(String thumbnailKey) {
        return new UpdateArtworkCommand(null, null, thumbnailKey, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private int thumbnailAssetCount(String artworkId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_assets WHERE owner_type = 'ARTWORK_THUMBNAIL' AND owner_id = ?",
                Integer.class, artworkId);
    }

    // 영구 삭제 이벤트는 작품의 R2 key 전체를 싣고 이벤트 레지스트리(EVENT_PUBLICATION.SERIALIZED_EVENT)에 저장된다.
    // V13의 VARCHAR(4000)이면 처리된 이미지가 많은 작품에서 'Data too long'으로 영구 삭제 전체가 롤백됐다(V40).
    @Test
    void 이미지가_많은_작품도_영구삭제된다() {
        String memberId = registerAuthor();
        List<String> keys = IntStream.range(0, 15)
                .mapToObj(i -> signedKey(memberId, "long-image-name-to-grow-the-serialized-event-".repeat(2) + i))
                .toList();
        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, baseUploadCommand(keys, List.of()));
        keys.forEach(key -> processImage(uploaded.id(), key, MediaProcessingStatus.DONE));
        awaitReady(memberId, uploaded.id());
        artworkService.deleteArtwork(memberId, uploaded.id());

        assertThatNoException().isThrownBy(() ->
                artworkService.permanentlyDeleteArtworks(memberId, List.of(uploaded.id())));
        assertThat(statusInDb(uploaded.id())).isNull();
    }

    // 휴지통 보관 기간(기본 1년) 만료 자동 영구 삭제(#178). 사용자 영구 삭제와 같은 경로를 거쳐야
    // 스냅샷 보존·R2 정리가 똑같이 적용되므로, 같은 이벤트가 같은 키 목록으로 나가는지 본다.
    @Test
    void 휴지통_보관_기간이_지난_작품은_자동으로_영구삭제된다(PublishedEvents events) {
        String memberId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "purge"));
        processImage(uploaded.id(), signedKey(memberId, "purge"), MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());
        artworkService.deleteArtwork(memberId, uploaded.id());
        setDeletedAt(uploaded.id(), Instant.now().minus(Duration.ofDays(366)));

        int purged = trashPurgeScheduler.purgeExpiredTrash();

        assertThat(purged).isEqualTo(1);
        assertThatThrownBy(() -> artworkService.getArtwork(uploaded.id(), memberId))
                .isInstanceOf(RuntimeException.class);
        assertThat(deletedImageKeysOf(events, uploaded.id())).contains(signedKey(memberId, "purge"));
    }

    @Test
    void 보관_기간이_남은_휴지통_작품과_휴지통_밖_작품은_자동삭제하지_않는다() {
        String memberId = registerAuthor();
        ArtworkInfo recentlyTrashed = uploadMinimal(memberId, signedKey(memberId, "recent"));
        artworkService.deleteArtwork(memberId, recentlyTrashed.id());
        setDeletedAt(recentlyTrashed.id(), Instant.now().minus(Duration.ofDays(364)));
        ArtworkInfo active = uploadMinimal(memberId, signedKey(memberId, "active"));
        // 휴지통 밖 작품에도 오래된 deleted_at을 넣는다 — 그래야 쿼리에서 status 조건이 빠졌을 때 걸린다.
        setDeletedAt(active.id(), Instant.now().minus(Duration.ofDays(366)));

        int purged = trashPurgeScheduler.purgeExpiredTrash();

        assertThat(purged).isZero();
        assertThat(statusInDb(recentlyTrashed.id())).isEqualTo("DELETED");
        assertThat(statusInDb(active.id())).isNotNull().isNotEqualTo("DELETED");
    }

    private String statusInDb(String artworkId) {
        return jdbcTemplate.queryForList("SELECT status FROM artworks WHERE id = ?", String.class, artworkId)
                .stream().findFirst().orElse(null);
    }

    private void setDeletedAt(String artworkId, Instant deletedAt) {
        jdbcTemplate.update("UPDATE artworks SET deleted_at = ? WHERE id = ?", Timestamp.from(deletedAt), artworkId);
    }

    /** 영구 삭제 이벤트가 실어 보낸 R2 key 목록 — 실제 R2 삭제는 비동기라 이벤트로 확인한다. */
    private List<String> deletedImageKeysOf(PublishedEvents events, String artworkId) {
        List<String> keys = new ArrayList<>();
        events.ofType(ArtworkPermanentlyDeletedEvent.class)
                .matching(event -> event.artworkId().equals(artworkId))
                .forEach(event -> keys.addAll(event.allImageKeys()));
        return keys;
    }

    // 운영 차단은 삭제·공개 상태보다 우선한다(마이페이지_작가-R39). 관리자 API가 없어 차단은 DB 직접
    // UPDATE로 이뤄지므로(docs/operations/moderation-block.md) 테스트도 같은 방식으로 재현한다.
    @Test
    void 운영_차단된_작품은_제3자에게_410이고_본인은_열람한다() {
        String memberId = registerAuthor();
        String viewerId = registerAuthor();
        ArtworkInfo uploaded = uploadMinimal(memberId, signedKey(memberId, "b1"));
        processImage(uploaded.id(), signedKey(memberId, "b1"), MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());

        blockArtwork(uploaded.id());

        assertThatThrownBy(() -> artworkService.getArtwork(uploaded.id(), viewerId))
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("ARTWORK_BLOCKED");
        assertThatThrownBy(() -> artworkService.getArtwork(uploaded.id(), null))
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.GONE);
        // 본인은 계속 열람할 수 있고, 응답의 blocked 플래그로 차단 안내 배지를 그린다.
        ArtworkInfo mine = artworkService.getArtwork(uploaded.id(), memberId);
        assertThat(mine.blocked()).isTrue();
    }

    /** 운영 차단 SQL 1건을 재현한다 — 관리자 API가 없어 실제 운영도 같은 UPDATE로 수행한다. */
    private void blockArtwork(String artworkId) {
        jdbcTemplate.update("UPDATE artworks SET blocked_at = UTC_TIMESTAMP(6) WHERE id = ?", artworkId);
    }

    /**
     * 고아 큐에 적재된 key 전부. 전체 행 수로 비교하면 앞선 테스트의 비동기 리스너가 남긴 행에 흔들린다 —
     * 이 테스트가 만든 key가 들어갔는지로 판정한다.
     */
    private List<String> orphanedKeys() {
        return jdbcTemplate.queryForList("select keys_json from orphaned_media_keys", String.class).stream()
                .flatMap(json -> java.util.Arrays.stream(json.replaceAll("[\\[\\]\"]", "").split(",")))
                .map(String::trim).filter(key -> !key.isEmpty()).toList();
    }

    /**
     * 본문 이미지의 Worker webhook 1건을 재현한다 — media가 자산 상태를 갱신하고 MediaAssetProcessedEvent를 발행한다.
     * 본문은 ORIGINAL 프로필이라 Worker가 original만 만든다.
     */
    private void processImage(String artworkId, String imageKey, MediaProcessingStatus status) {
        boolean done = status == MediaProcessingStatus.DONE;
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                null, null, done ? "original/" + variantName(imageKey) + ".avif" : null, status);
    }

    /** 사용자 지정 썸네일의 Worker webhook — THUMBNAIL_WITH_ADULT_BLUR라 thumb와 블러본만 만든다. */
    private void processThumbnail(String artworkId, String thumbnailKey) {
        String name = variantName(thumbnailKey);
        mediaCallbackService.process(MediaOwnerType.ARTWORK_THUMBNAIL, artworkId, thumbnailKey,
                "thumb/" + name + ".avif", "thumb-adult/" + name + ".avif", null, MediaProcessingStatus.DONE);
    }

    // Worker와 같은 규칙으로 변환 결과 이름을 만든다 — 마지막 경로 조각에서 확장자를 뗀다
    // (cloudflare-worker/src/index.js processOne). key에 소유자 서명 조각이 생겼어도 결과 이름은 그대로다(#190).
    private static String variantName(String key) {
        return key.substring(key.lastIndexOf('/') + 1, key.lastIndexOf('.'));
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
        Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
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
            uploadMinimal(memberId, signedKey(memberId, "starter-" + i));
        }

        assertThatThrownBy(() -> uploadMinimal(memberId, signedKey(memberId, "starter-5")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("STARTER_ARTWORK_LIMIT_EXCEEDED");
    }

    // 변환 실패로 이미지가 한 장도 안 남은 작품이 한도를 차지하면, 실패 때문에 재업로드까지 막혀
    // 사용자가 스스로 빠져나올 수 없다 — FAILED는 세지 않는다.
    @Test
    void 이미지가_전부_실패한_작품은_스타터_한도에_포함되지_않는다() {
        String memberId = registerAuthor();
        for (int i = 0; i < 3; i++) {
            uploadMinimal(memberId, signedKey(memberId, "quota-ok-" + i));
        }
        ArtworkInfo failed = uploadMinimal(memberId, signedKey(memberId, "quota-failed"));
        processImage(failed.id(), signedKey(memberId, "quota-failed"), MediaProcessingStatus.FAILED);
        awaitCondition(() -> artworkService.getArtworkStatus(memberId, failed.id()) == ArtworkStatus.FAILED);

        // 정상 3건 + 실패 1건이지만 한도(4)에 걸리지 않고 네 번째 정상 업로드가 통과한다.
        ArtworkInfo fourth = uploadMinimal(memberId, signedKey(memberId, "quota-ok-3"));
        assertThat(fourth.id()).isNotNull();

        assertThatThrownBy(() -> uploadMinimal(memberId, signedKey(memberId, "quota-over")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("STARTER_ARTWORK_LIMIT_EXCEEDED");
    }

    @Test
    void 프로_플랜은_작품_개수_제한이_없고_다운그레이드해도_기존_작품은_유지된다() {
        String memberId = registerAuthor();
        String subscriptionId = BillingTestSupport.grantProPlan(subscriptionRepository, memberId);
        for (int i = 0; i < 6; i++) {
            uploadMinimal(memberId, signedKey(memberId, "pro-" + i));
        }

        BillingTestSupport.cancelPlan(subscriptionRepository, subscriptionId);

        // 기존 산출물은 유지되고 신규 생성만 막힌다(요금제-R01)
        assertThat(artworkService.getMyArtworks(memberId, null, 20).items()).hasSize(6);
        assertThatThrownBy(() -> uploadMinimal(memberId, signedKey(memberId, "pro-after-downgrade")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("STARTER_ARTWORK_LIMIT_EXCEEDED");
    }

    @Test
    void 스타터는_작품_언어를_2개_이상_고를_수_없다() {
        String memberId = registerAuthor();

        assertThatThrownBy(() -> artworkService.uploadArtwork(memberId,
                uploadCommandWithLanguages(memberId, List.of(Language.KO, Language.JA))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("MULTI_LANGUAGE_REQUIRES_PRO");
    }

    @Test
    void 프로_플랜은_주_사용_언어에_다른_언어를_더할_수_있다() {
        String memberId = registerAuthorWithPrimaryLanguage(Language.KO);
        BillingTestSupport.grantProPlan(subscriptionRepository, memberId);

        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId,
                uploadCommandWithLanguages(memberId, List.of(Language.KO, Language.JA, Language.EN)));

        assertThat(uploaded.languages())
                .containsExactlyInAnyOrder(Language.KO, Language.JA, Language.EN);
    }

    @Test
    void 프로_플랜도_주_사용_언어를_빼면_거부된다() {
        String memberId = registerAuthorWithPrimaryLanguage(Language.KO);
        BillingTestSupport.grantProPlan(subscriptionRepository, memberId);

        assertThatThrownBy(() -> artworkService.uploadArtwork(memberId,
                uploadCommandWithLanguages(memberId, List.of(Language.JA, Language.EN))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("LANGUAGE_NOT_ALLOWED");
    }

    @Test
    void 스타터가_주_사용_언어를_포함해_2개를_고르면_다중_선택_오류가_난다() {
        String memberId = registerAuthorWithPrimaryLanguage(Language.KO);

        assertThatThrownBy(() -> artworkService.uploadArtwork(memberId,
                uploadCommandWithLanguages(memberId, List.of(Language.KO, Language.JA))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("MULTI_LANGUAGE_REQUIRES_PRO");
    }

    @Test
    void 스타터는_주_사용_언어가_아닌_언어로_게시할_수_없다() {
        String memberId = registerAuthorWithPrimaryLanguage(Language.KO);

        assertThatThrownBy(() -> artworkService.uploadArtwork(memberId,
                uploadCommandWithLanguages(memberId, List.of(Language.JA))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("LANGUAGE_NOT_ALLOWED");
    }

    @Test
    void 작품_이미지_변환_화질은_업로드_시점_플랜으로_갈린다() {
        String starter = registerAuthor();
        ArtworkInfo starterArtwork = uploadMinimal(starter, signedKey(starter, "tier-starter"));

        String pro = registerAuthor();
        BillingTestSupport.grantProPlan(subscriptionRepository, pro);
        ArtworkInfo proArtwork = uploadMinimal(pro, signedKey(pro, "tier-pro"));

        assertThat(qualityTierOf(starterArtwork.id())).isEqualTo("WEB");
        assertThat(qualityTierOf(proArtwork.id())).isEqualTo("ORIGINAL");
    }

    private String qualityTierOf(String artworkId) {
        return jdbcTemplate.queryForObject(
                "SELECT quality_tier FROM media_assets WHERE owner_type = 'ARTWORK' AND owner_id = ?",
                String.class, artworkId);
    }

    @Test
    void 이미지는_30장까지_업로드할_수_있고_31장이면_거부된다() {
        String memberId = registerAuthor();
        List<String> thirtyImages = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> signedKey(memberId, "bulk-" + i))
                .toList();

        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, baseUploadCommand(thirtyImages, List.of()));

        assertThat(uploaded.images()).hasSize(30);

        List<String> thirtyOneImages = new ArrayList<>(thirtyImages);
        thirtyOneImages.add(signedKey(memberId, "bulk-30"));
        assertThatThrownBy(() -> artworkService.uploadArtwork(memberId, baseUploadCommand(thirtyOneImages, List.of())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("INVALID_IMAGE_COUNT");
    }

    @Test
    void presign은_30개까지_발급되고_31개면_거부된다() {
        List<String> contentTypes30 = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> "image/png")
                .toList();

        List<PresignedUrlInfo> urls = artworkService.generatePresignedUrls(presignMemberId(), 30, contentTypes30, null);

        assertThat(urls).hasSize(30);

        List<String> contentTypes31 = new ArrayList<>(contentTypes30);
        contentTypes31.add("image/png");
        assertThatThrownBy(() -> artworkService.generatePresignedUrls(presignMemberId(), 31, contentTypes31, null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("INVALID_IMAGE_COUNT");
    }

    @Test
    void 커뮤니티_피드는_뷰어_언어와_겹치는_작품만_노출하고_언어_미지정_작품은_항상_노출한다() {
        String koAuthor = registerAuthor();
        String jaAuthor = registerAuthor();

        String koArtworkId = publishReady(koAuthor, signedKey(koAuthor, "feed-ko"), List.of(Language.KO));
        String jaArtworkId = publishReady(jaAuthor, signedKey(jaAuthor, "feed-ja"), List.of(Language.JA));
        // 마이그레이션 이전 작품 재현 — 언어 행이 없는 상태로 만든다
        String legacyArtworkId = publishReady(koAuthor, signedKey(koAuthor, "feed-legacy"), List.of(Language.KO));
        jdbcTemplate.update("DELETE FROM artwork_languages WHERE artwork_id = ?", legacyArtworkId);

        List<String> koViewerFeed = artworkService
                .getCommunityArtworks(null, null, List.of(Language.KO), null, 1, 50, null, true).items().stream().map(ArtworkSummaryInfo::id).toList();

        assertThat(koViewerFeed).contains(koArtworkId, legacyArtworkId);
        assertThat(koViewerFeed).doesNotContain(jaArtworkId);

        // 비로그인(빈 목록)은 필터를 적용하지 않는다
        List<String> anonymousFeed = artworkService
                .getCommunityArtworks(null, null, List.of(), null, 1, 50, null, true).items().stream().map(ArtworkSummaryInfo::id).toList();
        assertThat(anonymousFeed).contains(koArtworkId, jaArtworkId, legacyArtworkId);
    }

    @Test
    void 커뮤니티_피드는_표시_OFF_뷰어에게_R18_G18을_숨기되_본인_업로드는_항상_노출한다() {
        String author = registerAuthor();
        String viewer = registerAuthor();
        memberService.updateAdultContentVisible(viewer, false);

        String allArtworkId = publishReady(author, signedKey(author, "adult-all"), AgeRating.ALL);
        String r18ArtworkId = publishReady(author, signedKey(author, "adult-r18"), AgeRating.R18);
        String g18ArtworkId = publishReady(author, signedKey(author, "adult-g18"), AgeRating.G18);
        String ownR18ArtworkId = publishReady(viewer, signedKey(viewer, "adult-own-r18"), AgeRating.R18);

        List<String> hiddenFeed = artworkService
                .getCommunityArtworks(null, null, List.of(), null, 1, 50, viewer, false).items().stream().map(ArtworkSummaryInfo::id).toList();
        assertThat(hiddenFeed).contains(allArtworkId, ownR18ArtworkId);
        assertThat(hiddenFeed).doesNotContain(r18ArtworkId, g18ArtworkId);

        // 표시 ON이면 필터가 걸리지 않는다
        List<String> visibleFeed = artworkService
                .getCommunityArtworks(null, null, List.of(), null, 1, 50, viewer, true).items().stream().map(ArtworkSummaryInfo::id).toList();
        assertThat(visibleFeed).contains(allArtworkId, r18ArtworkId, g18ArtworkId, ownR18ArtworkId);
    }

    @Test
    void 커뮤니티_피드_전체_개수는_언어_세그먼트와_성인_콘텐츠_설정을_따른다() {
        String author = registerAuthor();
        String viewer = registerAuthor();
        long koBefore = artworkService
                .getCommunityArtworks(null, null, List.of(Language.KO), null, 1, 1, null, true).totalCount();
        long hiddenBefore = artworkService
                .getCommunityArtworks(null, null, List.of(), null, 1, 1, viewer, false).totalCount();

        publishReady(author, signedKey(author, "count-ko"), AgeRating.ALL, List.of(Language.KO));
        publishReady(author, signedKey(author, "count-ja"), AgeRating.ALL, List.of(Language.JA));
        publishReady(author, signedKey(author, "count-ko-r18"), AgeRating.R18, List.of(Language.KO));

        // KO 뷰어에게는 KO 작품 2건만 늘어난다(JA 작품 제외)
        assertThat(artworkService.getCommunityArtworks(null, null, List.of(Language.KO), null, 1, 1, null, true)
                .totalCount()).isEqualTo(koBefore + 2);
        // 성인 콘텐츠 표시 OFF 뷰어에게는 R18을 뺀 2건만 늘어난다
        assertThat(artworkService.getCommunityArtworks(null, null, List.of(), null, 1, 1, viewer, false)
                .totalCount()).isEqualTo(hiddenBefore + 2);

        // 개수는 같은 조건의 목록 건수와 일치한다
        OffsetPage<ArtworkSummaryInfo> koPage = artworkService
                .getCommunityArtworks(null, null, List.of(Language.KO), null, 1, 1000, null, true);
        assertThat(koPage.totalCount()).isEqualTo(koPage.items().size());
    }

    /** 피드는 READY 상태만 노출하므로 이미지 처리 콜백까지 재현해 공개 상태를 만든다. */
    private String publishReady(String memberId, String imageKey, List<Language> languages) {
        return publishReady(memberId, imageKey, AgeRating.ALL, languages);
    }

    private String publishReady(String memberId, String imageKey, AgeRating ageRating) {
        return publishReady(memberId, imageKey, ageRating, List.of(Language.KO));
    }

    private String publishReady(String memberId, String imageKey, AgeRating ageRating, List<Language> languages) {
        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(imageKey), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "피드 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                ageRating, languages, true, List.of(), List.of(), null, null, List.of(), List.of()));
        processImage(uploaded.id(), imageKey, MediaProcessingStatus.DONE);
        awaitReady(memberId, uploaded.id());
        return uploaded.id();
    }

    private UploadArtworkCommand uploadCommandWithLanguages(String memberId, List<Language> languages) {
        return new UploadArtworkCommand(
                List.of(signedKey(memberId, "lang")), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "테스트 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, languages, true, List.of(), List.of(), null, null, List.of(), List.of());
    }

    private String registerAuthorWithPrimaryLanguage(Language primaryLanguage) {
        return memberService.register(new RegisterMemberCommand(
                "lang-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "언어작가", AuthProvider.EMAIL, "Secure1!",
                true, true, true, false, "Asia/Seoul", "KR", primaryLanguage)).id();
    }

    private ArtworkInfo uploadMinimal(String memberId, String... imageKeys) {
        return artworkService.uploadArtwork(memberId, baseUploadCommand(List.of(imageKeys), List.of()));
    }

    private UploadArtworkCommand baseUploadCommand(List<String> imageKeys, List<MaterialData> materials) {
        return new UploadArtworkCommand(
                imageKeys, 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "테스트 작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), materials);
    }

    /** presign은 이제 회원마다 다른 서명을 key에 넣는다(#190) — 발급 대상이 필요하다. */
    private String presignMemberId() {
        return registerAuthor();
    }

    /**
     * 그 회원에게 발급된 것과 같은 형태의 업로드 key(#190). 이름을 고정해야 변환 결과 key(thumb/…)를 테스트가
     * 예측할 수 있어 presign을 부르지 않고 같은 규칙으로 만든다.
     */
    private String signedKey(String memberId, String name) {
        return "raw/" + keySigner.sign(memberId, name) + "/" + name + ".png";
    }

    private String registerAuthor() {
        return memberService.register(
                "artwork-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "aw" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "작가").id();
    }
}
