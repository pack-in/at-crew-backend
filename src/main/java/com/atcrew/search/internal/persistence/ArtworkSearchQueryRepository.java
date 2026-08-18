package com.atcrew.search.internal.persistence;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.atcrew.search.SearchQuery;
import com.atcrew.search.SearchSort;
import com.atcrew.search.internal.domain.ArtworkSearchDocument;
import com.atcrew.search.internal.exception.SearchErrorCode;
import com.atcrew.search.internal.exception.SearchException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 다축 필터(작품 분야·창작 유형·연령대·담당 업무·장르·소재 대상) + 텍스트 검색 + 커서(search_after) 조합 조회.
 * 축 내부는 OR(terms), 축 간에는 AND(bool filter) — 피그마 다중선택 규칙(docs/design/search-module-design.md §1.5).
 *
 * <p>단건 upsert/삭제는 {@link ArtworkSearchRepository} 참고.
 */
@Component
public class ArtworkSearchQueryRepository {

    private final ElasticsearchOperations operations;

    public ArtworkSearchQueryRepository(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public ArtworkSearchResult search(SearchQuery query) {
        boolean relevance = resolveSort(query) == SearchSort.RELEVANCE;

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(buildQuery(query))
                .withTrackTotalHits(true)
                .withPageable(PageRequest.ofSize(query.size() + 1))
                .withSort(buildSort(relevance))
                .withSearchAfter(query.cursor() != null ? decodeCursor(query.cursor(), relevance) : null)
                .build();

        SearchHits<ArtworkSearchDocument> hits = operations.search(nativeQuery, ArtworkSearchDocument.class);
        List<SearchHit<ArtworkSearchDocument>> hitList = hits.getSearchHits();

        boolean hasNext = hitList.size() > query.size();
        List<SearchHit<ArtworkSearchDocument>> page = hasNext ? hitList.subList(0, query.size()) : hitList;

        String nextCursor = hasNext
                ? SearchCursor.encode(new ArrayList<>(page.get(page.size() - 1).getSortValues()))
                : null;

        return new ArtworkSearchResult(
                page.stream().map(SearchHit::getContent).toList(),
                nextCursor,
                hasNext,
                hits.getTotalHits()
        );
    }

    private SearchSort resolveSort(SearchQuery query) {
        if (query.sort() != null) return query.sort();
        return (query.q() != null && !query.q().isBlank()) ? SearchSort.RELEVANCE : SearchSort.LATEST;
    }

    private Query buildQuery(SearchQuery query) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (query.q() != null && !query.q().isBlank()) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(query.q())
                    .fields("title^3", "description", "authorName", "tags")));
        } else {
            bool.must(m -> m.matchAll(ma -> ma));
        }
        addTerms(bool, "artworkField", names(query.artworkFields()));
        addTerms(bool, "creativeType", names(query.creativeTypes()));
        addTerms(bool, "ageRating", names(query.ageRatings()));
        addTerms(bool, "roles", names(query.roles()));
        addTerms(bool, "genres", names(query.genres()));
        addTerms(bool, "materialTargets", names(query.materialTargets()));
        return Query.of(q -> q.bool(bool.build()));
    }

    private void addTerms(BoolQuery.Builder bool, String field, List<String> values) {
        if (values == null || values.isEmpty()) return;
        bool.filter(f -> f.terms(t -> t
                .field(field)
                .terms(ts -> ts.value(values.stream().map(FieldValue::of).toList()))));
    }

    private <E extends Enum<E>> List<String> names(List<E> values) {
        return values == null ? List.of() : values.stream().map(Enum::name).toList();
    }

    private List<SortOptions> buildSort(boolean relevance) {
        List<SortOptions> sorts = new ArrayList<>();
        if (relevance) {
            sorts.add(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))));
        }
        sorts.add(SortOptions.of(so -> so.field(f -> f.field("createdAt").order(SortOrder.Desc))));
        sorts.add(SortOptions.of(so -> so.field(f -> f.field("id").order(SortOrder.Asc))));
        return sorts;
    }

    private List<Object> decodeCursor(String cursor, boolean relevance) {
        List<String> parts = SearchCursor.decode(cursor);
        try {
            List<Object> values = new ArrayList<>();
            int idx = 0;
            if (relevance) {
                values.add(Double.valueOf(parts.get(idx++)));
            }
            values.add(Long.valueOf(parts.get(idx++)));
            values.add(parts.get(idx));
            return values;
        } catch (RuntimeException e) {
            throw new SearchException(SearchErrorCode.INVALID_CURSOR);
        }
    }
}
