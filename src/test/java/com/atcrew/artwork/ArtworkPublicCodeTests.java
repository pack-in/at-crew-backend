package com.atcrew.artwork;

import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.id.PublicCodeGenerator;
import com.atcrew.media.internal.application.MediaKeySigner;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import com.atcrew.support.DatabaseCleanupExtension;
import com.atcrew.support.ScriptedRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 작품 6자 공개 코드 발급 검증 (docs/design/member-handle-design.md §3, §5).
 *
 * <p>충돌 경로를 결정적으로 재현하려고 {@link PublicCodeGenerator}를 {@link ScriptedRandom} 주입본으로 바꾼다.
 * 스크립트를 넣지 않은 발급은 SecureRandom 그대로다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
@Import(ArtworkPublicCodeTests.ScriptedCodes.class)
class ArtworkPublicCodeTests {

    @TestConfiguration
    static class ScriptedCodes {
        @Bean
        ScriptedRandom scriptedRandom() {
            return new ScriptedRandom();
        }

        @Bean
        @Primary
        PublicCodeGenerator scriptedPublicCodeGenerator(ScriptedRandom scriptedRandom) {
            return new PublicCodeGenerator(scriptedRandom);
        }
    }

    @Autowired
    ScriptedRandom scriptedRandom;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    ArtworkRepository artworkRepository;

    @Autowired
    MemberService memberService;

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

    @BeforeEach
    void resetScript() {
        scriptedRandom.clear();
    }

    @Test
    void 작품을_올리면_6자_공개_코드가_발급된다() {
        String authorId = registerAuthor();

        String artworkId = upload(authorId).id();

        assertThat(publicCodeOf(artworkId)).matches("^[a-z0-9]{6}$");
    }

    @Test
    void 코드는_작가_안에서만_유일하고_다른_작가와는_겹쳐도_된다() {
        String authorA = registerAuthor();
        String authorB = registerAuthor();
        scriptedRandom.enqueue("aaaaaa", "aaaaaa");

        String first = upload(authorA).id();
        String second = upload(authorB).id();

        assertThat(publicCodeOf(first)).isEqualTo("aaaaaa");
        assertThat(publicCodeOf(second)).isEqualTo("aaaaaa");
    }

    @Test
    void 같은_작가의_기존_코드와_겹치면_다시_뽑는다() {
        String authorId = registerAuthor();
        scriptedRandom.enqueue("aaaaaa");
        String first = upload(authorId).id();

        scriptedRandom.enqueue("aaaaaa", "aaaaaa", "bbbbbb");
        String second = upload(authorId).id();

        assertThat(publicCodeOf(first)).isEqualTo("aaaaaa");
        assertThat(publicCodeOf(second)).isEqualTo("bbbbbb");
    }

    @Test
    void 세_번_모두_겹치면_코드_발급_실패로_끝나고_작품은_저장되지_않는다() {
        String authorId = registerAuthor();
        scriptedRandom.enqueue("aaaaaa");
        upload(authorId);

        scriptedRandom.enqueue("aaaaaa", "aaaaaa", "aaaaaa");
        assertThatThrownBy(() -> upload(authorId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("PUBLIC_CODE_GENERATION_FAILED");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        assertThat(artworkRepository.findAllByAuthorId(authorId)).hasSize(1);
    }

    @Test
    void 휴지통_작품도_코드를_유지해_같은_작가의_새_작품과_겹치지_않는다() {
        String authorId = registerAuthor();
        scriptedRandom.enqueue("aaaaaa");
        String trashed = upload(authorId).id();
        artworkService.deleteArtwork(authorId, trashed);

        assertThat(publicCodeOf(trashed)).isEqualTo("aaaaaa");

        scriptedRandom.enqueue("aaaaaa", "cccccc");
        String fresh = upload(authorId).id();
        assertThat(publicCodeOf(fresh)).isEqualTo("cccccc");

        artworkService.restoreArtworks(authorId, List.of(trashed));
        assertThat(publicCodeOf(trashed)).isEqualTo("aaaaaa");
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private ArtworkInfo upload(String authorId) {
        String name = UUID.randomUUID().toString();
        String key = "raw/" + keySigner.sign(authorId, name) + "/" + name + ".png";
        return artworkService.uploadArtwork(authorId, new UploadArtworkCommand(
                List.of(key), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
    }

    private String publicCodeOf(String artworkId) {
        return artworkRepository.findById(artworkId).orElseThrow().getPublicCode();
    }

    private String registerAuthor() {
        return memberService.register(
                "code-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "cd" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "작가").id();
    }
}
