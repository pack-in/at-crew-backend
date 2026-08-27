package com.atcrew.common.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sentry 전송 전 마스킹")
class SentryConfigTest {

    @Test
    void 이메일은_마스킹된다() {
        String masked = SentryConfig.mask("가입 실패: email=hong@example.com");

        assertThat(masked).doesNotContain("hong@example.com");
        assertThat(masked).contains("@");
    }

    @Test
    void 문장_안의_이메일_여러_개를_모두_마스킹한다() {
        String masked = SentryConfig.mask("a@a.com 과 bb@bbb.co.kr 충돌");

        assertThat(masked).doesNotContain("a@a.com").doesNotContain("bb@bbb.co.kr");
    }

    @Test
    void JWT는_통째로_제거한다() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSJ9.abcDEF-_123";

        assertThat(SentryConfig.mask("Authorization: Bearer " + jwt))
                .doesNotContain(jwt)
                .contains("<redacted-token>");
    }

    @Test
    void 마스킹할_것이_없으면_원문_그대로다() {
        String text = "서버 오류: ARTWORK_NOT_FOUND artworkId=01912f0a";

        assertThat(SentryConfig.mask(text)).isEqualTo(text);
    }

    @Test
    void null과_빈_문자열을_그대로_돌려준다() {
        assertThat(SentryConfig.mask(null)).isNull();
        assertThat(SentryConfig.mask("")).isEmpty();
    }
}
