package com.atcrew.member.internal.application;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 프로필 섹션별 완성도·변경 필드 수 계산(plans/261005-posthog-analytics-events PA-04,
 * FE 회신(PH-02) 2026-10-07 반영).
 *
 * <p>{@code Member.profileFieldSnapshot()}으로 뽑은 23개 필드 값 목록({@code UpdateInfoCommand}와
 * 1:1 순서 대응)을 받아 순수하게 계산한다. 외부 상태(DB·트랜잭션)에 의존하지 않아 단위 테스트로
 * 바로 검증할 수 있다.
 *
 * <p>저장 API(PATCH /members/me/info)는 기본정보/구직정보 필드를 한 번에 받지만, 프론트 Figma
 * 화면(UI개편_마이페이지_작가_수정페이지, node 4971:25431)은 "기본 정보"/"구직 정보" 두 탭으로
 * 나뉘어 있다. FE 요청대로 실제로 바뀐 필드를 보고 섹션을 가려 {@code profile_section_saved}를
 * 섹션별로(둘 다 바뀌면 이벤트 2개로) 낸다 — {@link MemberServiceImpl#updateInfo}가 이 클래스의
 * {@code ARTIST_INFO_INDEXES}/{@code JOB_INFO_INDEXES}로 구간을 나눠 호출한다.
 *
 * <p>"채워짐" 판정: null이면 미입력. 컬렉션은 JPA {@code @ElementCollection} 필드가 항상 빈
 * 컬렉션으로 초기화돼 있어(절대 null 아님) null 체크만으로는 모든 회원이 항상 "채워짐"으로
 * 집계되므로 비어 있으면 미입력으로 본다. 문자열은 blank(공백만)도 미입력으로 본다.
 *
 * <p>각 섹션의 completion_rate 분모에서는 가입 시점에 이미 값이 들어가는 필드를 뺀다 — 사용자가
 * 직접 채운 적 없어도 항상 "채워짐"으로 잡혀 완성도 하한이 0%가 아니게 되는 문제가 있었다
 * (code-review 2026-10-06): {@code employmentStatus}(기본값 PREPARING), {@code totalSlotCount}/
 * {@code availableSlotCount}(기본값 5, primitive int라 "비어 있음"이 없음), {@code timezone}/
 * {@code countryCode}(가입 커맨드가 항상 채움). {@code changed_field_count}는 섹션 전체 필드를
 * 그대로 본다 — 이 필드들이 바뀌어도 변경으로 셀 가치가 있다.
 */
final class ProfileCompletionCalculator {

    // profileFieldSnapshot() 순서:
    // 0 employmentStatus, 1 activityFields, 2 experienceLevel, 3 activeRegion, 4 totalSlotCount,
    // 5 availableSlotCount, 6 teamExperiences, 7 drawingStyles, 8 workPace, 9 availableStartPeriod,
    // 10 desiredRoles, 11 desiredGenres, 12 desiredEmploymentTypes, 13 desiredWorkLocation,
    // 14 feedbackPreferences, 15 desiredMinimumGuarantee, 16 desiredAnnualSalary, 17 customTags,
    // 18 contact, 19 sns, 20 tools, 21 timezone, 22 countryCode

    /**
     * 기본 정보 탭(Figma "기본 정보") — 활동 분야·경력·지역·팀 경험·그림체·작업 역량·태그·연락처·SNS·
     * 사용 툴·시간대·거주국가. timezone/countryCode는 이 화면 밖(가입)에서도 채워지지만 저장 API가
     * 같이 받으므로 여기 묶는다.
     */
    static final List<Integer> ARTIST_INFO_INDEXES = List.of(1, 2, 3, 6, 7, 8, 17, 18, 19, 20, 21, 22);

    /**
     * 구직 정보 탭(Figma "구직 정보") — 구인구직 상태·슬롯·투입 가능 시기·희망 담당업무/장르/채용형태/
     * 근무방식·선호 피드백 방식·희망 MG·희망 연봉.
     */
    static final List<Integer> JOB_INFO_INDEXES = List.of(0, 4, 5, 9, 10, 11, 12, 13, 14, 15, 16);

    // 가입 시점에 항상 채워지는 필드 — completion_rate 분모에서만 뺀다(두 섹션 인덱스 기준 혼합).
    private static final Set<Integer> ALWAYS_FILLED_AT_SIGNUP = Set.of(0, 4, 5, 21, 22);

    private ProfileCompletionCalculator() {
    }

    /** 섹션에 속한 필드 중 실제로 값이 다른 필드 수. */
    static int sectionChangedFieldCount(List<Object> before, List<Object> after, List<Integer> sectionIndexes) {
        int changed = 0;
        for (int i : sectionIndexes) {
            if (!Objects.equals(before.get(i), after.get(i))) {
                changed++;
            }
        }
        return changed;
    }

    /** 섹션 완성도 — 가입 시 항상 채워지는 필드는 분모·분자 둘 다에서 뺀다. */
    static double sectionCompletionRate(List<Object> fields, List<Integer> sectionIndexes) {
        int target = 0;
        int filled = 0;
        for (int i : sectionIndexes) {
            if (ALWAYS_FILLED_AT_SIGNUP.contains(i)) {
                continue;
            }
            target++;
            if (isFilled(fields.get(i))) {
                filled++;
            }
        }
        return target == 0 ? 0.0 : filled / (double) target;
    }

    private static boolean isFilled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        return true;
    }
}
