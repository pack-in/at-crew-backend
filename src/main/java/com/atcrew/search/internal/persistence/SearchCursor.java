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
 *
 * <p>포트폴리오와 recruit 결과를 함께 노출할 때는 두 소스가 같은 커서를 공유해야 하므로(최신순 병합)
 * 서비스 계층도 이 인코딩을 사용한다.
 */
public class SearchCursor {

    private static final String DELIMITER = "_";

    private SearchCursor() {
    }

    public static String encode(List<Object> sortValues) {
        String joined = sortValues.stream().map(String::valueOf).collect(Collectors.joining(DELIMITER));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    public static List<String> decode(String cursor) {
        try {
            String joined = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Arrays.asList(joined.split(DELIMITER, -1));
        } catch (IllegalArgumentException e) {
            throw new SearchException(SearchErrorCode.INVALID_CURSOR);
        }
    }
}
