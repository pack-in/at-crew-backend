package com.atcrew.member.internal.application;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 프로필 완성도·변경 필드 수 계산(plans/261005-posthog-analytics-events PA-04).
 *
 * <p>{@code Member.profileFieldSnapshot()}으로 뽑은 23개 필드 값 목록({@code UpdateInfoCommand}와
 * 1:1 순서 대응)을 받아 순수하게 계산한다. 외부 상태(DB·트랜잭션)에 의존하지 않아 단위 테스트로
 * 바로 검증할 수 있다.
 *
 * <p>"채워짐" 판정: null이면 미입력. 컬렉션은 JPA {@code @ElementCollection} 필드가 항상 빈
 * 컬렉션으로 초기화돼 있어(절대 null 아님) null 체크만으로는 모든 회원이 항상 "채워짐"으로
 * 집계되므로 비어 있으면 미입력으로 본다. 문자열은 blank(공백만)도 미입력으로 본다.
 *
 * <p>completion_rate 분모에서는 가입 시점에 이미 값이 들어가는 필드 5개를 뺀다 — 사용자가 직접
 * 채운 적 없어도 항상 "채워짐"으로 잡혀 완성도 하한이 0%가 아니게 되는 문제가 있었다(code-review
 * 2026-10-06): {@code employmentStatus}(기본값 PREPARING), {@code totalSlotCount}/
 * {@code availableSlotCount}(기본값 5, primitive int라 "비어 있음"이 없음), {@code timezone}/
 * {@code countryCode}(가입 커맨드가 항상 채움). {@code changed_field_count}는 그대로 23개 전체를 본다
 * — "뭘 고쳤는지"는 이 5개가 바뀌어도 변경으로 셀 가치가 있다.
 */
final class ProfileCompletionCalculator {

    // UpdateInfoCommand 전체 필드 수 — Member.profileFieldSnapshot()과 1:1 대응. changed_field_count는
    // 이 전체를 본다.
    static final int TOTAL_FIELD_COUNT = 23;

    // profileFieldSnapshot() 순서 기준 — employmentStatus(0), totalSlotCount(4), availableSlotCount(5),
    // timezone(21), countryCode(22). 가입 시 항상 값이 들어가 completion_rate 분모에서 뺀다.
    private static final Set<Integer> ALWAYS_FILLED_AT_SIGNUP = Set.of(0, 4, 5, 21, 22);

    static final int TARGET_FIELD_COUNT = TOTAL_FIELD_COUNT - ALWAYS_FILLED_AT_SIGNUP.size();

    private ProfileCompletionCalculator() {
    }

    static double completionRate(List<Object> fields) {
        return filledCount(fields) / (double) TARGET_FIELD_COUNT;
    }

    static int filledCount(List<Object> fields) {
        int count = 0;
        for (int i = 0; i < fields.size(); i++) {
            if (ALWAYS_FILLED_AT_SIGNUP.contains(i)) {
                continue;
            }
            if (isFilled(fields.get(i))) {
                count++;
            }
        }
        return count;
    }

    /** 같은 순서의 두 스냅샷을 필드별로 비교해 값이 다른 필드 수를 센다. */
    static int changedFieldCount(List<Object> before, List<Object> after) {
        int changed = 0;
        for (int i = 0; i < before.size(); i++) {
            if (!Objects.equals(before.get(i), after.get(i))) {
                changed++;
            }
        }
        return changed;
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
