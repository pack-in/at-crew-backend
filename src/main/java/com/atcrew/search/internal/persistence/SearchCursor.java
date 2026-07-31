package com.atcrew.search.internal.persistence;

import com.atcrew.search.internal.exception.SearchErrorCode;
import com.atcrew.search.internal.exception.SearchException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * ES {@code search_after} 커서 인코딩/디코딩. 정렬 값 튜플을 구분자로 이어붙여 base64(URL-safe)로
 * 감싼 불투명 문자열로 노출한다(docs/design/search-module-design.md §3).
 *
 * <p>정렬 모드에 따라 값 개수가 다르다 — RELEVANCE: [score, createdAtMillis, id], LATEST: [createdAtMillis, id].
 * 타입 변환은 정렬 모드를 아는 {@link ArtworkSearchQueryRepository}가 담당한다.
 */
class SearchCursor {

    private static final String DELIMITER = "_";

    private SearchCursor() {
    }

    static String encode(List<Object> sortValues) {
        String joined = sortValues.stream().map(String::valueOf).collect(Collectors.joining(DELIMITER));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    static List<String> decode(String cursor) {
        try {
            String joined = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Arrays.asList(joined.split(DELIMITER, -1));
        } catch (IllegalArgumentException e) {
            throw new SearchException(SearchErrorCode.INVALID_CURSOR);
        }
    }
}
