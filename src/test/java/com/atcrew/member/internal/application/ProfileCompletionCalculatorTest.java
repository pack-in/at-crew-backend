package com.atcrew.member.internal.application;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로필 완성도·변경 필드 수 계산 단위 테스트(plans/261005-posthog-analytics-events PA-04).
 *
 * <p>Member.profileFieldSnapshot()이 실제로 뽑는 값 대신, 같은 자리수(23개)의 임의 값으로
 * 순수 함수만 검증한다 — DB·Spring 컨텍스트가 필요 없다.
 */
class ProfileCompletionCalculatorTest {

    // 23칸짜리 "완전히 빈" 스냅샷 — null·빈 컬렉션·빈 문자열만으로 구성
    private static List<Object> emptySnapshot() {
        return Arrays.asList(
                null, List.of(), null, null,
                null, null, List.of(), List.of(), null,
                null, List.of(), List.of(), List.of(),
                null, List.of(), null, null,
                List.of(), null, null, null, null, null
        );
    }

    @Test
    void 모든_필드가_null이거나_빈_값이면_완성도는_0이다() {
        List<Object> snapshot = emptySnapshot();

        assertThat(ProfileCompletionCalculator.filledCount(snapshot)).isEqualTo(0);
        assertThat(ProfileCompletionCalculator.completionRate(snapshot)).isZero();
    }

    @Test
    void 빈_컬렉션은_null과_마찬가지로_미입력으로_센다() {
        List<Object> snapshot = emptySnapshot();
        snapshot.set(1, List.of()); // activityFields 자리 — 빈 리스트

        assertThat(ProfileCompletionCalculator.filledCount(snapshot)).isEqualTo(0);
    }

    @Test
    void 공백만_있는_문자열은_미입력으로_센다() {
        List<Object> snapshot = emptySnapshot();
        snapshot.set(18, "   "); // contact 자리

        assertThat(ProfileCompletionCalculator.filledCount(snapshot)).isEqualTo(0);
    }

    @Test
    void 일부_필드만_채워지면_그만큼만_완성도에_반영된다() {
        List<Object> snapshot = emptySnapshot();
        snapshot.set(0, "AVAILABLE"); // employmentStatus
        snapshot.set(18, "010-1234-5678"); // contact

        assertThat(ProfileCompletionCalculator.filledCount(snapshot)).isEqualTo(2);
        assertThat(ProfileCompletionCalculator.completionRate(snapshot)).isEqualTo(2 / 23.0);
    }

    @Test
    void 모든_필드가_채워지면_완성도는_1이다() {
        List<Object> snapshot = Arrays.asList(
                "AVAILABLE", List.of("ILLUSTRATION"), "THREE_TO_FOUR", "SEOUL",
                5, 3, List.of("TEAM"), List.of("STYLE"), "STEADY",
                "WITHIN_A_MONTH", List.of("STORYBOARD"), List.of("BL"), List.of("FULL_TIME"),
                "REMOTE", List.of("TEXT"), "NEGOTIABLE", "RANGE_3000",
                List.of("tag"), "010-1234-5678", "sns", "tools", "Asia/Seoul", "KR"
        );

        assertThat(ProfileCompletionCalculator.completionRate(snapshot)).isEqualTo(1.0);
    }

    @Test
    void 두_스냅샷을_비교해_값이_다른_필드_수를_센다() {
        List<Object> before = emptySnapshot();
        List<Object> after = emptySnapshot();
        after.set(0, "AVAILABLE");
        after.set(8, "STEADY");
        after.set(18, "010-1234-5678");

        assertThat(ProfileCompletionCalculator.changedFieldCount(before, after)).isEqualTo(3);
    }

    @Test
    void 바뀐_필드가_없으면_0을_반환한다() {
        List<Object> before = emptySnapshot();
        List<Object> after = emptySnapshot();

        assertThat(ProfileCompletionCalculator.changedFieldCount(before, after)).isEqualTo(0);
    }
}
