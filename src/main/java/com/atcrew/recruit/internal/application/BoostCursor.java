package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;

import java.time.Instant;

/**
 * 끌어올리기 상단고정이 적용된 공개 목록의 커서 (설계 §2.1.1).
 *
 * <p>정렬 키가 (끌어올리기 정렬값, id) 2단이므로 커서도 두 값을 함께 담아야 keyset 페이지네이션이 성립한다.
 * 끌어올리기가 없거나 만료된 글의 정렬값은 {@link Instant#EPOCH}로 정규화한다.
 *
 * @param boostSortAt 정렬 키 1 — 적용 중이면 boostedUntil, 아니면 EPOCH
 * @param id          정렬 키 2 — 글 ID(UUIDv7)
 */
record BoostCursor(Instant boostSortAt, String id) {

    private static final String DELIMITER = "_";

    String encode() {
        return boostSortAt.toEpochMilli() + DELIMITER + id;
    }

    static BoostCursor decode(String cursor) {
        int delimiterIndex = cursor.indexOf(DELIMITER);
        if (delimiterIndex <= 0 || delimiterIndex == cursor.length() - 1) {
            throw new RecruitException(RecruitErrorCode.INVALID_CURSOR, "cursor=" + cursor);
        }
        try {
            Instant boostSortAt = Instant.ofEpochMilli(Long.parseLong(cursor.substring(0, delimiterIndex)));
            return new BoostCursor(boostSortAt, cursor.substring(delimiterIndex + 1));
        } catch (NumberFormatException e) {
            throw new RecruitException(RecruitErrorCode.INVALID_CURSOR, "cursor=" + cursor);
        }
    }

    // 목록 응답의 다음 커서 — 마지막 항목의 정렬 키를 그대로 담는다.
    static String encodeOf(Instant boostedUntil, String id, Instant now) {
        Instant boostSortAt = boostedUntil != null && now.isBefore(boostedUntil) ? boostedUntil : Instant.EPOCH;
        return new BoostCursor(boostSortAt, id).encode();
    }
}
