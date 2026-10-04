package com.atcrew.member.internal.application;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
 */
final class ProfileCompletionCalculator {

    // UpdateInfoCommand 전체 필드 수 — Member.profileFieldSnapshot()과 1:1 대응
    static final int TARGET_FIELD_COUNT = 23;

    private ProfileCompletionCalculator() {
    }

    static double completionRate(List<Object> fields) {
        return filledCount(fields) / (double) TARGET_FIELD_COUNT;
    }

    static int filledCount(List<Object> fields) {
        int count = 0;
        for (Object field : fields) {
            if (isFilled(field)) {
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
