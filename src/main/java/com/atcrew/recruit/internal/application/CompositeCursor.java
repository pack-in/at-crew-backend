package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;

import java.time.Instant;

/**
 * (시각, ID) 2단 정렬 목록용 커서.
 *
 * <p>끌어올리기 상단고정 목록(설계 §2.1.1)이나 관심 작가 목록(§2.7)처럼 정렬 키가 시각 하나로 유일하지 않은
 * 목록은 커서에 두 값을 함께 담아야 keyset 페이지네이션이 성립한다.
 *
 * @param sortAt 정렬 키 1 — 시각(끌어올리기 정렬값, 저장 시각, 조회 시각 등)
 * @param id     정렬 키 2 — 같은 시각을 구분하는 ID
 */
record CompositeCursor(Instant sortAt, String id) {

    private static final String DELIMITER = "_";

    String encode() {
        return sortAt.toEpochMilli() + DELIMITER + id;
    }

    static String encode(Instant sortAt, String id) {
        return new CompositeCursor(sortAt, id).encode();
    }

    static CompositeCursor decode(String cursor) {
        int delimiterIndex = cursor.indexOf(DELIMITER);
        if (delimiterIndex <= 0 || delimiterIndex == cursor.length() - 1) {
            throw new RecruitException(RecruitErrorCode.INVALID_CURSOR, "cursor=" + cursor);
        }
        try {
            Instant sortAt = Instant.ofEpochMilli(Long.parseLong(cursor.substring(0, delimiterIndex)));
            return new CompositeCursor(sortAt, cursor.substring(delimiterIndex + 1));
        } catch (NumberFormatException e) {
            throw new RecruitException(RecruitErrorCode.INVALID_CURSOR, "cursor=" + cursor);
        }
    }

    /**
     * 끌어올리기 목록의 다음 커서 — 적용 중이면 boostedUntil, 아니면 {@link Instant#EPOCH}로 정규화한다(§2.1.1).
     */
    static String encodeBoost(Instant boostedUntil, String id, Instant now) {
        Instant sortAt = boostedUntil != null && now.isBefore(boostedUntil) ? boostedUntil : Instant.EPOCH;
        return encode(sortAt, id);
    }
}
