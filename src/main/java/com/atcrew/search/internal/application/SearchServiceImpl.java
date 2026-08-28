package com.atcrew.search.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.search.PostType;
import com.atcrew.search.SearchPage;
import com.atcrew.search.SearchQuery;
import com.atcrew.search.SearchResultItem;
import com.atcrew.search.SearchService;
import com.atcrew.search.SearchSort;
import com.atcrew.search.internal.domain.ArtworkSearchDocument;
import com.atcrew.search.internal.domain.RecruitSearchDocument;
import com.atcrew.search.internal.exception.SearchErrorCode;
import com.atcrew.search.internal.exception.SearchException;
import com.atcrew.search.internal.persistence.ArtworkSearchQueryRepository;
import com.atcrew.search.internal.persistence.ArtworkSearchResult;
import com.atcrew.search.internal.persistence.MergedSearchCursor;
import com.atcrew.search.internal.persistence.RecruitSearchQueryRepository;
import com.atcrew.search.internal.persistence.RecruitSearchResult;
import com.atcrew.search.internal.persistence.SearchCursor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
class SearchServiceImpl implements SearchService {

    /**
     * 소스 통합 정렬 키 — 최신순(작성 시각 내림차순), 같은 시각은 ID 오름차순.
     * 각 소스 자체의 keyset 정렬 순서와 동일해야 병합 결과 안에서도 소스별 상대 순서가 유지된다.
     */
    private static final Comparator<SearchResultItem> LATEST_ORDER = Comparator
            .comparing(SearchResultItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SearchResultItem::id);

    private final ArtworkSearchQueryRepository artworkSearchQueryRepository;
    private final RecruitSearchQueryRepository recruitSearchQueryRepository;

    SearchServiceImpl(ArtworkSearchQueryRepository artworkSearchQueryRepository,
                       RecruitSearchQueryRepository recruitSearchQueryRepository) {
        this.artworkSearchQueryRepository = artworkSearchQueryRepository;
        this.recruitSearchQueryRepository = recruitSearchQueryRepository;
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
        if (searchesRecruit) {
            // 통합검색(searchMerged)은 관련도 비교 불가로 항상 최신순 병합한다(withLatestSort) — OLDEST를
            // 조용히 최신순으로 바꿔치기하지 않고 명시적으로 거절한다(이슈 #79 팔로업). postTypes 미지정
            // 검색(가장 흔한 형태)도 이 분기를 탄다는 점이 처음엔 눈에 안 띄어서 별도로 남긴다.
            if (query.sort() == SearchSort.OLDEST) {
                throw new SearchException(SearchErrorCode.UNSUPPORTED_SORT_FOR_MERGED_SEARCH);
            }
            return searchMerged(query);
        }
        return searchPortfolio(query);
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
        RecruitSearchResult recruitResult = recruitSearchQueryRepository.search(query);
        List<SearchResultItem> items = recruitResult.items().stream()
                .map(this::toResultItem)
                .toList();
        return new SearchPage<>(items, recruitResult.nextCursor(), recruitResult.hasNext(),
                recruitResult.totalCount());
    }

    /**
     * 포트폴리오와 recruit 결과를 하나의 페이지로 병합한다(docs/design/search-module-design.md §8 미결 사항 해소).
     *
     * <p>관련도 점수는 두 소스 간 비교가 불가능하므로, 통합 결과는 정렬 요청과 무관하게 항상 최신순으로 병합한다.
     * 두 소스의 id는 서로 무관한 값이라 하나의 커서를 공유할 수 없으므로, 각 소스는 {@link MergedSearchCursor}가
     * 감싼 자기 서브커서만으로 독립적으로 조회한다(다른 소스의 id를 몰라도 된다).
     */
    private SearchPage<SearchResultItem> searchMerged(SearchQuery query) {
        SearchQuery latestQuery = withLatestSort(query);
        MergedSearchCursor.SubCursors subCursors = query.cursor() != null
                ? MergedSearchCursor.decode(query.cursor())
                : new MergedSearchCursor.SubCursors(null, null);

        ArtworkSearchResult artworkResult = artworkSearchQueryRepository.search(
                withCursor(latestQuery, subCursors.artworkCursor()));
        RecruitSearchResult recruitResult = recruitSearchQueryRepository.search(
                withCursor(latestQuery, subCursors.recruitCursor()));

        List<SearchResultItem> merged = new ArrayList<>();
        artworkResult.items().forEach(doc -> merged.add(toResultItem(doc)));
        recruitResult.items().forEach(doc -> merged.add(toResultItem(doc)));
        merged.sort(LATEST_ORDER);

        // 각 소스에서 size건까지 가져오므로, 병합 결과가 size를 넘으면 이번 페이지에 담지 못한 항목이 남아 있다.
        boolean hasNext = artworkResult.hasNext() || recruitResult.hasNext() || merged.size() > query.size();
        List<SearchResultItem> items = merged.size() > query.size()
                ? List.copyOf(merged.subList(0, query.size()))
                : List.copyOf(merged);
        String nextCursor = hasNext ? encodeNextCursor(items, subCursors) : null;
        return new SearchPage<>(items, nextCursor, hasNext, artworkResult.totalCount() + recruitResult.totalCount());
    }

    /**
     * 다음 페이지 서브커서를 계산한다. 이번 페이지에 결과를 낸 소스는 자기 마지막 항목 위치로 갱신하고,
     * 결과를 내지 못한 소스(아직 시작 전이거나, 조회는 됐지만 병합·컷 과정에서 전부 잘려나간 경우)는
     * 이전 서브커서를 그대로 유지한다 — hasNext=false는 "내부적으로 더 없다"는 신호일 뿐 "이번 페이지에
     * 다 반환했다"는 신호가 아니므로, 소스 자체의 hasNext로 서브커서를 앞당기지 않는다.
     */
    private String encodeNextCursor(List<SearchResultItem> items, MergedSearchCursor.SubCursors previous) {
        String artworkCursor = previous.artworkCursor();
        String recruitCursor = previous.recruitCursor();
        for (SearchResultItem item : items) {
            if (item.postType() == PostType.PORTFOLIO) {
                artworkCursor = encodeCursor(item);
            } else {
                recruitCursor = encodeCursor(item);
            }
        }
        return MergedSearchCursor.encode(new MergedSearchCursor.SubCursors(artworkCursor, recruitCursor));
    }

    private SearchQuery withCursor(SearchQuery query, String cursor) {
        return new SearchQuery(query.q(), query.postTypes(), query.artworkFields(), query.creativeTypes(),
                query.ageRatings(), query.roles(), query.genres(), query.materialTargets(),
                query.viewerLanguages(), query.sort(), cursor, query.size());
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

    private SearchQuery withLatestSort(SearchQuery query) {
        return new SearchQuery(query.q(), query.postTypes(), query.artworkFields(), query.creativeTypes(),
                query.ageRatings(), query.roles(), query.genres(), query.materialTargets(),
                query.viewerLanguages(), SearchSort.LATEST, query.cursor(), query.size());
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
    private SearchResultItem toResultItem(RecruitSearchDocument doc) {
        return new SearchResultItem(
                doc.getId(),
                PostType.valueOf(doc.getPostType()),
                doc.getTitle(),
                doc.getThumbnailKey(),
                null,
                doc.getAuthorId(),
                doc.getAuthorName(),
                null,
                null,
                doc.getCreatedAt()
        );
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
