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
        SearchSort sort = resolveSort(query);

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(buildQuery(query))
                .withTrackTotalHits(true)
                .withPageable(PageRequest.ofSize(query.size() + 1))
                .withSort(buildSort(sort))
                .withSearchAfter(query.cursor() != null ? decodeCursor(query.cursor(), sort) : null)
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
        addLanguageSegment(bool, names(query.viewerLanguages()));
        addAdultContentFilter(bool, query.viewerAdultContentVisible(), query.viewerMemberId());
        return Query.of(q -> q.bool(bool.build()));
    }

    /**
     * 언어 세그먼트 필터(로그인-R16) — 다른 축과 달리 "값 없음"도 통과시켜야 한다.
     * 언어를 고른 적 없는 문서(마이그레이션 이전 작품)까지 걸러내면 기존 작품이 검색에서 사라진다.
     */
    private void addLanguageSegment(BoolQuery.Builder bool, List<String> viewerLanguages) {
        if (viewerLanguages == null || viewerLanguages.isEmpty()) return;
        bool.filter(f -> f.bool(b -> b
                .should(s -> s.terms(t -> t
                        .field("languages")
                        .terms(ts -> ts.value(viewerLanguages.stream().map(FieldValue::of).toList()))))
                .should(s -> s.bool(nb -> nb.mustNot(mn -> mn.exists(e -> e.field("languages")))))
                .minimumShouldMatch("1")));
    }

    /**
     * 성인 콘텐츠 표시 필터(설정-R10) — 표시 OFF일 때 R18/G18 중 본인 업로드가 아닌 작품을 제외한다.
     * 본인 업로드분은 표시 설정과 무관하게 항상 노출된다(마이페이지_작가-R21).
     */
    private void addAdultContentFilter(BoolQuery.Builder bool, boolean viewerAdultContentVisible,
                                        String viewerMemberId) {
        if (viewerAdultContentVisible) return;
        bool.filter(f -> f.bool(b -> {
            b.should(s -> s.bool(nb -> nb.mustNot(mn -> mn.terms(t -> t
                    .field("ageRating")
                    .terms(ts -> ts.value(List.of(FieldValue.of("R18"), FieldValue.of("G18"))))))));
            if (viewerMemberId != null) {
                b.should(s -> s.term(t -> t.field("authorId").value(viewerMemberId)));
            }
            return b.minimumShouldMatch("1");
        }));
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

    private List<SortOptions> buildSort(SearchSort sort) {
        List<SortOptions> sorts = new ArrayList<>();
        if (sort == SearchSort.RELEVANCE) {
            sorts.add(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))));
        }
        SortOrder createdAtOrder = sort == SearchSort.OLDEST ? SortOrder.Asc : SortOrder.Desc;
        sorts.add(SortOptions.of(so -> so.field(f -> f.field("createdAt").order(createdAtOrder))));
        sorts.add(SortOptions.of(so -> so.field(f -> f.field("id").order(SortOrder.Asc))));
        return sorts;
    }

    private List<Object> decodeCursor(String cursor, SearchSort sort) {
        List<String> parts = SearchCursor.decode(cursor);
        try {
            List<Object> values = new ArrayList<>();
            int idx = 0;
            if (sort == SearchSort.RELEVANCE) {
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
