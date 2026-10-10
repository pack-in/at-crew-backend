package com.atcrew.member.internal.application;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로필 섹션별 완성도·변경 필드 수 계산 단위 테스트(plans/261005-posthog-analytics-events PA-04,
 * FE 회신 PH-02 2026-10-07 반영).
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
    void 모든_필드가_비어있으면_두_섹션_다_완성도는_0이다() {
        List<Object> snapshot = emptySnapshot();

        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.ARTIST_INFO_INDEXES)).isZero();
        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.JOB_INFO_INDEXES)).isZero();
    }

    // code-review 2026-10-06 — 가입 때부터 항상 값이 있는 필드(기본정보: timezone/countryCode,
    // 구직정보: employmentStatus/totalSlotCount/availableSlotCount)만 채워진 회원은 실질적으로
    // 아무것도 안 적은 것과 같다.
    @Test
    void 가입시_항상_채워지는_필드만_있으면_완성도는_여전히_0이다() {
        List<Object> snapshot = emptySnapshot();
        snapshot.set(0, "PREPARING");    // employmentStatus 기본값
        snapshot.set(4, 5);              // totalSlotCount 기본값
        snapshot.set(5, 5);              // availableSlotCount 기본값
        snapshot.set(21, "Asia/Seoul");  // timezone
        snapshot.set(22, "KR");          // countryCode

        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.ARTIST_INFO_INDEXES)).isZero();
        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.JOB_INFO_INDEXES)).isZero();
    }

    @Test
    void 기본정보_필드_일부만_채워지면_기본정보_완성도에만_반영된다() {
        List<Object> snapshot = emptySnapshot();
        snapshot.set(1, List.of("ILLUSTRATION")); // activityFields
        snapshot.set(18, "010-1234-5678");        // contact

        // 기본정보 대상 10개 중 2개 — 구직정보는 전혀 안 건드림
        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.ARTIST_INFO_INDEXES)).isEqualTo(2 / 10.0);
        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.JOB_INFO_INDEXES)).isZero();
    }

    @Test
    void 구직정보_필드_일부만_채워지면_구직정보_완성도에만_반영된다() {
        List<Object> snapshot = emptySnapshot();
        snapshot.set(10, List.of("작화")); // desiredRoles

        // 구직정보 대상 8개 중 1개
        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.JOB_INFO_INDEXES)).isEqualTo(1 / 8.0);
        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.ARTIST_INFO_INDEXES)).isZero();
    }

    @Test
    void 기본정보_대상_필드가_전부_채워지면_기본정보_완성도는_1이다() {
        List<Object> snapshot = emptySnapshot();
        snapshot.set(1, List.of("ILLUSTRATION"));  // activityFields
        snapshot.set(2, "THREE_TO_FOUR");          // experienceLevel
        snapshot.set(3, "SEOUL");                  // activeRegion
        snapshot.set(6, List.of("TEAM"));          // teamExperiences
        snapshot.set(7, List.of("STYLE"));         // drawingStyles
        snapshot.set(8, "STEADY");                 // workPace
        snapshot.set(17, List.of("tag"));          // customTags
        snapshot.set(18, "010-1234-5678");         // contact
        snapshot.set(19, "sns");                   // sns
        snapshot.set(20, "tools");                 // tools
        // timezone/countryCode(21,22)는 가입시 항상 채워지는 필드라 분모에서 빠진다 — 비워 둬도 1.0

        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.ARTIST_INFO_INDEXES)).isEqualTo(1.0);
    }

    @Test
    void 구직정보_대상_필드가_전부_채워지면_구직정보_완성도는_1이다() {
        List<Object> snapshot = emptySnapshot();
        snapshot.set(9, "WITHIN_A_MONTH");                 // availableStartPeriod
        snapshot.set(10, List.of("STORYBOARD"));           // desiredRoles
        snapshot.set(11, List.of("BL"));                   // desiredGenres
        snapshot.set(12, List.of("FULL_TIME"));            // desiredEmploymentTypes
        snapshot.set(13, "REMOTE");                        // desiredWorkLocation
        snapshot.set(14, List.of("TEXT"));                 // feedbackPreferences
        snapshot.set(15, "NEGOTIABLE");                    // desiredMinimumGuarantee
        snapshot.set(16, "RANGE_3000");                    // desiredAnnualSalary
        // employmentStatus/totalSlotCount/availableSlotCount(0,4,5)는 분모에서 빠진다

        assertThat(ProfileCompletionCalculator.sectionCompletionRate(snapshot,
                ProfileCompletionCalculator.JOB_INFO_INDEXES)).isEqualTo(1.0);
    }

    @Test
    void 섹션에_속하지_않은_필드가_바뀌어도_그_섹션의_변경_수에는_안_잡힌다() {
        List<Object> before = emptySnapshot();
        List<Object> after = emptySnapshot();
        after.set(10, List.of("작화")); // desiredRoles — job_info 소속

        assertThat(ProfileCompletionCalculator.sectionChangedFieldCount(before, after,
                ProfileCompletionCalculator.ARTIST_INFO_INDEXES)).isZero();
        assertThat(ProfileCompletionCalculator.sectionChangedFieldCount(before, after,
                ProfileCompletionCalculator.JOB_INFO_INDEXES)).isEqualTo(1);
    }

    @Test
    void 두_섹션_필드가_동시에_바뀌면_각각_따로_센다() {
        List<Object> before = emptySnapshot();
        List<Object> after = emptySnapshot();
        after.set(1, List.of("ILLUSTRATION")); // activityFields — artist_info
        after.set(8, "STEADY");                // workPace — artist_info
        after.set(10, List.of("작화"));         // desiredRoles — job_info

        assertThat(ProfileCompletionCalculator.sectionChangedFieldCount(before, after,
                ProfileCompletionCalculator.ARTIST_INFO_INDEXES)).isEqualTo(2);
        assertThat(ProfileCompletionCalculator.sectionChangedFieldCount(before, after,
                ProfileCompletionCalculator.JOB_INFO_INDEXES)).isEqualTo(1);
    }

    @Test
    void 바뀐_필드가_없으면_두_섹션_다_0을_반환한다() {
        List<Object> before = emptySnapshot();
        List<Object> after = emptySnapshot();

        assertThat(ProfileCompletionCalculator.sectionChangedFieldCount(before, after,
                ProfileCompletionCalculator.ARTIST_INFO_INDEXES)).isZero();
        assertThat(ProfileCompletionCalculator.sectionChangedFieldCount(before, after,
                ProfileCompletionCalculator.JOB_INFO_INDEXES)).isZero();
    }
}
