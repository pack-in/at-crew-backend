package com.atcrew.search.internal.persistence;

import com.atcrew.search.internal.domain.ArtworkSearchDocument;

import java.util.List;

/** {@link ArtworkSearchQueryRepository}의 원시 조회 결과 — 서비스 계층 응답 타입({@code SearchPage})으로 변환되기 전 단계. */
public record ArtworkSearchResult(
        List<ArtworkSearchDocument> items,
        String nextCursor,
        boolean hasNext,
        long totalCount
) {
}
