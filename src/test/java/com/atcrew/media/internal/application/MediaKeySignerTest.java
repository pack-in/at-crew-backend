package com.atcrew.media.internal.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * 업로드 key의 소유자 서명(#190).
 *
 * <p>key는 공개 API 응답에 그대로 실린다. 서명이 없던 시절에는 남의 key를 수집해 자기 작품의 지정 썸네일로
 * 넣고 영구 삭제하면 남의 R2 파일이 지워졌다.
 */
class MediaKeySignerTest {

    private final MediaKeySigner signer = new MediaKeySigner("secret-one", List.of());

    @Test
    void 발급받은_회원만_자기_key로_인정된다() {
        String key = keyFor("member-1", "01a0-uuid");

        assertThat(signer.isOwnedBy(key, "member-1")).isTrue();
        assertThat(signer.isOwnedBy(key, "member-2")).isFalse();
    }

    @Test
    void 서명이_변조되면_거부한다() {
        String key = keyFor("member-1", "01a0-uuid");
        String tampered = key.replaceFirst("raw/.", "raw/z");

        assertThat(signer.isOwnedBy(tampered, "member-1")).isFalse();
    }

    // 같은 회원이라도 key마다 서명이 달라야 한다 — 고정값이면 공개된 key를 모아 같은 업로더의 파일을 묶어볼 수 있다.
    @Test
    void 같은_회원의_다른_key는_서명이_다르다() {
        String first = signer.sign("member-1", "uuid-a");
        String second = signer.sign("member-1", "uuid-b");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 서명이_없는_옛_형식과_변환_결과_key는_거부한다() {
        assertThat(signer.isOwnedBy("raw/01a0-uuid.png", "member-1")).isFalse();
        assertThat(signer.isOwnedBy("thumb/01a0-uuid.avif", "member-1")).isFalse();
        assertThat(signer.isOwnedBy("original/01a0-uuid.avif", "member-1")).isFalse();
        assertThat(signer.isOwnedBy("raw/a/b/01a0-uuid.png", "member-1")).isFalse();
        assertThat(signer.isOwnedBy(null, "member-1")).isFalse();
    }

    // 비밀값을 바꾸면 그 전에 발급된 key가 전부 검증에 실패한다 — 이전 값을 함께 등록해 유예한다.
    @Test
    void 이전_비밀값으로_서명된_key도_인정한다() {
        String oldKey = keyFor("member-1", "01a0-uuid");
        MediaKeySigner rotated = new MediaKeySigner("secret-two", List.of("secret-one"));

        assertThat(rotated.isOwnedBy(oldKey, "member-1")).isTrue();
        // 새 발급은 새 비밀값으로 한다.
        assertThat(rotated.sign("member-1", "01a0-uuid")).isNotEqualTo(signer.sign("member-1", "01a0-uuid"));
    }

    @Test
    void 비밀값이_비어_있으면_기동하지_않는다() {
        assertThatIllegalStateException().isThrownBy(() -> new MediaKeySigner("  ", List.of()));
    }

    private String keyFor(String memberId, String uuid) {
        return "raw/" + signer.sign(memberId, uuid) + "/" + uuid + ".png";
    }
}
