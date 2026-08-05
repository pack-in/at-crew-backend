package com.atcrew.search.internal.persistence;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.atcrew.search.PostType;
import com.atcrew.search.SearchQuery;
import com.atcrew.search.SearchSort;
import com.atcrew.search.internal.domain.RecruitSearchDocument;
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
 * 구인글·팀원모집글·구직글 제목 검색 + 담당업무/장르 다축 필터 + 커서(search_after) 조합 조회.
 * {@link ArtworkSearchQueryRepository}와 동일한 구조다(docs/design/search-module-design.md §3) —
 * artwork 전용 축(작품 분야·창작 유형·연령대·소재 대상)은 recruit 게시글에 없으므로 참조하지 않는다.
 *
 * <p>단건 upsert/삭제는 {@link RecruitSearchRepository} 참고.
 */
@Component
public class RecruitSearchQueryRepository {

    private final ElasticsearchOperations operations;

    public RecruitSearchQueryRepository(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public RecruitSearchResult search(SearchQuery query) {
        boolean relevance = resolveSort(query) == SearchSort.RELEVANCE;

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(buildQuery(query))
                .withTrackTotalHits(true)
                .withPageable(PageRequest.ofSize(query.size() + 1))
                .withSort(buildSort(relevance))
                .withSearchAfter(query.cursor() != null ? decodeCursor(query.cursor(), relevance) : null)
                .build();

        SearchHits<RecruitSearchDocument> hits = operations.search(nativeQuery, RecruitSearchDocument.class);
        List<SearchHit<RecruitSearchDocument>> hitList = hits.getSearchHits();

        boolean hasNext = hitList.size() > query.size();
        List<SearchHit<RecruitSearchDocument>> page = hasNext ? hitList.subList(0, query.size()) : hitList;

        String nextCursor = hasNext
                ? SearchCursor.encode(new ArrayList<>(page.get(page.size() - 1).getSortValues()))
                : null;

        return new RecruitSearchResult(
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
                    .fields("title^3")));
        } else {
            bool.must(m -> m.matchAll(ma -> ma));
        }
        addPostTypeFilter(bool, query.postTypes());
        // 담당 업무 필터는 enum이지만 recruit의 태그는 작성자가 입력한 자유 문자열이라 상수 이름으로 비교한다 —
        // 정본 태그 목록이 확정되면 함께 정규화한다(docs/design/search-module-design.md §9-2).
        addTerms(bool, "roles", names(query.roles()));
        addTerms(bool, "genres", query.genres());
        return Query.of(q -> q.bool(bool.build()));
    }

    // postTypes가 비어 있으면(전체 유형 대상) 필터를 걸지 않는다. PORTFOLIO는 recruit 문서에 없는 값이라 제외한다.
    private void addPostTypeFilter(BoolQuery.Builder bool, List<PostType> postTypes) {
        if (postTypes == null || postTypes.isEmpty()) return;
        List<String> types = postTypes.stream()
                .filter(type -> type != PostType.PORTFOLIO)
                .map(Enum::name)
                .toList();
        if (!types.isEmpty()) {
            addTerms(bool, "postType", types);
        }
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
