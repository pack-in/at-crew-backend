package com.atcrew.search.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.recruit.RecruitPostType;
import com.atcrew.recruit.RecruitSearchPage;
import com.atcrew.recruit.RecruitSearchQuery;
import com.atcrew.recruit.RecruitSearchResultInfo;
import com.atcrew.recruit.RecruitService;
import com.atcrew.search.PostType;
import com.atcrew.search.SearchPage;
import com.atcrew.search.SearchQuery;
import com.atcrew.search.SearchResultItem;
import com.atcrew.search.SearchService;
import com.atcrew.search.SearchSort;
import com.atcrew.search.internal.domain.ArtworkSearchDocument;
import com.atcrew.search.internal.exception.SearchErrorCode;
import com.atcrew.search.internal.exception.SearchException;
import com.atcrew.search.internal.persistence.ArtworkSearchQueryRepository;
import com.atcrew.search.internal.persistence.ArtworkSearchResult;
import com.atcrew.search.internal.persistence.SearchCursor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
class SearchServiceImpl implements SearchService {

    /**
     * 소스 통합 정렬 키 — 최신순(작성 시각 내림차순), 같은 시각은 ID 오름차순.
     * 커서(createdAtMillis, id)와 동일한 순서라야 두 소스가 커서를 공유할 수 있다.
     */
    private static final Comparator<SearchResultItem> LATEST_ORDER = Comparator
            .comparing(SearchResultItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SearchResultItem::id);

    private final ArtworkSearchQueryRepository artworkSearchQueryRepository;
    private final RecruitService recruitService;

    SearchServiceImpl(ArtworkSearchQueryRepository artworkSearchQueryRepository,
                       RecruitService recruitService) {
        this.artworkSearchQueryRepository = artworkSearchQueryRepository;
        this.recruitService = recruitService;
    }

    @Override
    public SearchPage<SearchResultItem> search(SearchQuery query) {
        if (query.isEmptyCriteria()) {
            // 피그마 "최초 진입 시 결과 미노출" 규칙 (docs/design/search-module-design.md §1.5)
            return SearchPage.empty();
        }

        boolean searchesRecruit = query.includesRecruitOwned() && appliesToRecruit(query);
        if (!query.includesPortfolio()) {
            // postTypes가 recruit 소유 유형만 지정된 경우 — 전량 위임
            return searchesRecruit ? searchRecruit(query) : SearchPage.empty();
        }
        return searchesRecruit ? searchMerged(query) : searchPortfolio(query);
    }

    private SearchPage<SearchResultItem> searchPortfolio(SearchQuery query) {
        ArtworkSearchResult artworkResult = artworkSearchQueryRepository.search(query);
        List<SearchResultItem> items = artworkResult.items().stream()
                .map(this::toResultItem)
                .toList();
        return new SearchPage<>(items, artworkResult.nextCursor(), artworkResult.hasNext(),
                artworkResult.totalCount());
    }

    private SearchPage<SearchResultItem> searchRecruit(SearchQuery query) {
        RecruitSearchPage recruitPage = recruitService.searchPosts(toRecruitQuery(query));
        List<SearchResultItem> items = recruitPage.items().stream()
                .map(this::toResultItem)
                .toList();
        String nextCursor = recruitPage.hasNext() ? encodeCursor(items.get(items.size() - 1)) : null;
        return new SearchPage<>(items, nextCursor, recruitPage.hasNext(), recruitPage.totalCount());
    }

    /**
     * 포트폴리오와 recruit 결과를 하나의 페이지로 병합한다(docs/design/search-module-design.md §8 미결 사항 해소).
     *
     * <p>관련도 점수는 recruit 소스에 없어 소스 간 비교가 불가능하므로, 통합 결과는 정렬 요청과 무관하게
     * 항상 최신순으로 병합한다. 두 소스 모두 (작성 시각, ID) keyset 커서를 지원해 커서 하나로 위치를 공유한다.
     */
    private SearchPage<SearchResultItem> searchMerged(SearchQuery query) {
        ArtworkSearchResult artworkResult = artworkSearchQueryRepository.search(withLatestSort(query));
        RecruitSearchPage recruitPage = recruitService.searchPosts(toRecruitQuery(query));

        List<SearchResultItem> merged = new ArrayList<>();
        artworkResult.items().forEach(doc -> merged.add(toResultItem(doc)));
        recruitPage.items().forEach(item -> merged.add(toResultItem(item)));
        merged.sort(LATEST_ORDER);

        // 각 소스에서 size건까지 가져오므로, 병합 결과가 size를 넘으면 이번 페이지에 담지 못한 항목이 남아 있다.
        boolean hasNext = artworkResult.hasNext() || recruitPage.hasNext() || merged.size() > query.size();
        List<SearchResultItem> items = merged.size() > query.size()
                ? List.copyOf(merged.subList(0, query.size()))
                : List.copyOf(merged);
        String nextCursor = hasNext ? encodeCursor(items.get(items.size() - 1)) : null;
        return new SearchPage<>(items, nextCursor, hasNext, artworkResult.totalCount() + recruitPage.totalCount());
    }

    /**
     * recruit 소스에 적용 가능한 조건인지 판별한다.
     * 구인글/구직글/팀원모집글에는 작품 분야·창작 유형·연령대·소재 대상 속성이 없으므로, 축 간 AND 결합 규칙상
     * 해당 필터가 하나라도 걸리면 recruit 결과는 존재할 수 없다(§1.5).
     */
    private boolean appliesToRecruit(SearchQuery query) {
        return isEmpty(query.artworkFields()) && isEmpty(query.creativeTypes())
                && isEmpty(query.ageRatings()) && isEmpty(query.materialTargets());
    }

    private RecruitSearchQuery toRecruitQuery(SearchQuery query) {
        Instant cursorCreatedAt = null;
        String cursorId = null;
        if (query.cursor() != null) {
            List<String> parts = SearchCursor.decode(query.cursor());
            try {
                cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(parts.get(0)));
                cursorId = parts.get(1);
            } catch (RuntimeException e) {
                throw new SearchException(SearchErrorCode.INVALID_CURSOR);
            }
        }
        // 담당 업무 필터는 enum이지만 recruit의 태그는 작성자가 입력한 자유 문자열이라 상수 이름으로 비교한다 —
        // 정본 태그 목록이 확정되면 함께 정규화한다(docs/design/search-module-design.md §9-2).
        return new RecruitSearchQuery(query.q(), toRecruitPostTypes(query.postTypes()),
                names(query.roles()), query.genres(), cursorCreatedAt, cursorId, query.size());
    }

    // postTypes가 비어 있으면 recruit 3종 전체가 대상이므로 null로 넘긴다.
    private List<RecruitPostType> toRecruitPostTypes(List<PostType> postTypes) {
        if (isEmpty(postTypes)) {
            return null;
        }
        return postTypes.stream()
                .filter(type -> type != PostType.PORTFOLIO)
                .map(type -> RecruitPostType.valueOf(type.name()))
                .toList();
    }

    private SearchQuery withLatestSort(SearchQuery query) {
        return new SearchQuery(query.q(), query.postTypes(), query.artworkFields(), query.creativeTypes(),
                query.ageRatings(), query.roles(), query.genres(), query.materialTargets(),
                SearchSort.LATEST, query.cursor(), query.size());
    }

    private String encodeCursor(SearchResultItem item) {
        return SearchCursor.encode(List.of(item.createdAt().toEpochMilli(), item.id()));
    }

    private SearchResultItem toResultItem(ArtworkSearchDocument doc) {
        return new SearchResultItem(
                doc.getId(),
                PostType.PORTFOLIO,
                doc.getTitle(),
                doc.getThumbnailKey(),
                doc.getThumbnailAdultKey(),
                doc.getAuthorId(),
                doc.getAuthorName(),
                doc.getAuthorHandle(),
                doc.getAgeRating() != null ? AgeRating.valueOf(doc.getAgeRating()) : null,
                doc.getCreatedAt()
        );
    }

    /**
     * recruit 결과 → 공통 검색 카드 변환. 성인 썸네일·작성자 핸들·연령 등급은 recruit이 보유하지 않는 값이라
     * 항상 null이다(recruit 콘텐츠는 연령 게이팅 대상이 아님 — docs/design/recruit-module-design.md §7).
     */
    private SearchResultItem toResultItem(RecruitSearchResultInfo item) {
        return new SearchResultItem(
                item.id(),
                PostType.valueOf(item.postType().name()),
                item.title(),
                item.thumbnailUrl(),
                null,
                item.authorMemberId(),
                item.authorName(),
                null,
                null,
                item.createdAt()
        );
    }

    private <E extends Enum<E>> List<String> names(List<E> values) {
        return values == null ? null : values.stream().map(Enum::name).toList();
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
