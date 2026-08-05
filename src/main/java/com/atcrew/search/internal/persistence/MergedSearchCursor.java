package com.atcrew.search.internal.persistence;

import com.atcrew.search.internal.exception.SearchErrorCode;
import com.atcrew.search.internal.exception.SearchException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 병합검색(포트폴리오+recruit) 커서 — 소스별 서브커서를 하나의 불투명 문자열로 감싼다
 * (docs/design/search-module-design.md §8 미결 사항 해소).
 *
 * <p>각 서브커서는 {@link ArtworkSearchQueryRepository}/{@link RecruitSearchQueryRepository}가
 * 자체 조회 결과로 만들어내는 {@link SearchCursor} 인코딩 문자열을 그대로 담는다 — 이 클래스는
 * 정렬 값 타입을 몰라도 되며, 소스별 재조회 시 그대로 전달하기만 하면 된다. 두 소스의 id는 서로
 * 무관한 값이라 하나의 커서로 비교할 수 없다는 문제(위 설계 문서에 지목됨)를 이렇게 해소한다.
 */
public class MergedSearchCursor {

    public record SubCursors(String artworkCursor, String recruitCursor) {
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MergedSearchCursor() {
    }

    public static String encode(SubCursors subCursors) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(subCursors);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("병합검색 커서를 인코딩하는 데 실패했습니다", e);
        }
    }

    public static SubCursors decode(String cursor) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return OBJECT_MAPPER.readValue(json, SubCursors.class);
        } catch (Exception e) {
            throw new SearchException(SearchErrorCode.INVALID_CURSOR);
        }
    }
}
