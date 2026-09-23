package com.atcrew.media.internal.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * presign 발급 한도(#216). 인증만 통과하면 무제한이었고, 발급받은 URL로 올린 파일은 작품·게시글에 등록되기
 * 전까지 어디에도 기록되지 않아 아무도 모르는 원본이 쌓였다.
 */
class PresignRateLimiterTest {

    @Test
    void 창_안에서_한도까지_발급하고_넘으면_거부한다() {
        PresignRateLimiter limiter = new PresignRateLimiter(30, Duration.ofHours(1));

        assertThat(limiter.tryIssue("member-1", 30)).isTrue();
        assertThat(limiter.tryIssue("member-1", 1)).isFalse();
    }

    // 요청 수가 아니라 key 수를 센다 — 한 번에 30장과 한 장씩 30번이 같은 무게여야 한다.
    @Test
    void 요청_수가_아니라_발급_장수를_센다() {
        PresignRateLimiter limiter = new PresignRateLimiter(3, Duration.ofHours(1));

        assertThat(limiter.tryIssue("member-1", 1)).isTrue();
        assertThat(limiter.tryIssue("member-1", 1)).isTrue();
        assertThat(limiter.tryIssue("member-1", 1)).isTrue();
        assertThat(limiter.tryIssue("member-1", 1)).isFalse();
    }

    // 거부된 요청은 기록에 남기지 않는다 — 남기면 한도를 넘긴 시도만으로 다음 요청까지 계속 막힌다.
    @Test
    void 거부된_요청은_한도를_더_먹지_않는다() {
        PresignRateLimiter limiter = new PresignRateLimiter(5, Duration.ofHours(1));

        assertThat(limiter.tryIssue("member-1", 4)).isTrue();
        assertThat(limiter.tryIssue("member-1", 30)).isFalse();
        assertThat(limiter.tryIssue("member-1", 1)).isTrue();
    }

    @Test
    void 회원마다_따로_센다() {
        PresignRateLimiter limiter = new PresignRateLimiter(1, Duration.ofHours(1));

        assertThat(limiter.tryIssue("member-1", 1)).isTrue();
        assertThat(limiter.tryIssue("member-2", 1)).isTrue();
        assertThat(limiter.tryIssue("member-1", 1)).isFalse();
    }

    @Test
    void 창이_지나면_다시_발급할_수_있다() throws InterruptedException {
        PresignRateLimiter limiter = new PresignRateLimiter(1, Duration.ofMillis(50));

        assertThat(limiter.tryIssue("member-1", 1)).isTrue();
        assertThat(limiter.tryIssue("member-1", 1)).isFalse();
        Thread.sleep(60);
        assertThat(limiter.tryIssue("member-1", 1)).isTrue();
    }

    @Test
    void 설정값이_유효하지_않으면_기동하지_않는다() {
        assertThatIllegalStateException().isThrownBy(() -> new PresignRateLimiter(0, Duration.ofHours(1)));
        assertThatIllegalStateException().isThrownBy(() -> new PresignRateLimiter(30, Duration.ZERO));
        assertThatIllegalStateException().isThrownBy(() -> new PresignRateLimiter(30, Duration.ofHours(-1)));
    }
}
