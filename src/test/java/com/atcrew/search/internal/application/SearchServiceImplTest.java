package com.atcrew.search.internal.application;

import com.atcrew.search.PostType;
import com.atcrew.search.SearchPage;
import com.atcrew.search.SearchQuery;
import com.atcrew.search.SearchResultItem;
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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * searchMerged의 소스별 서브커서 재설계 검증 — 실제 ES 없이 리포지토리를 목으로 대체해
 * 커서 계산 로직만 확인한다(전체 파이프라인 통합 검증은 {@code SearchModuleTests} 참고).
 */
class SearchServiceImplTest {

    private static final long COLLIDING_MILLIS = 1_700_000_000_000L;

    private final ArtworkSearchQueryRepository artworkRepo = mock(ArtworkSearchQueryRepository.class);
    private final RecruitSearchQueryRepository recruitRepo = mock(RecruitSearchQueryRepository.class);
    private final SearchServiceImpl service = new SearchServiceImpl(artworkRepo, recruitRepo);

    @Test
    void postTypes_미지정_통합검색에서_OLDEST_요청은_최신순으로_바뀌지_않고_400으로_거절된다() {
        SearchQuery query = new SearchQuery("고양이", null, null, null, null, null, null, null, null,
                SearchSort.OLDEST, null, 20);

        assertThatThrownBy(() -> service.search(query))
                .isInstanceOf(SearchException.class)
                .extracting(e -> ((SearchException) e).getCode())
                .isEqualTo(SearchErrorCode.UNSUPPORTED_SORT_FOR_MERGED_SEARCH.name());
        verifyNoInteractions(artworkRepo, recruitRepo);
    }

    @Test
    void 밀리초_타임스탬프가_겹치는_두_소스_항목이_경계에서_누락되지_않는다() {
        ArtworkSearchDocument artworkDoc = artworkDoc("art-1", COLLIDING_MILLIS);
        RecruitSearchDocument recruitDoc = recruitDoc("recruit-1", COLLIDING_MILLIS);

        // 1페이지: 두 소스 모두 커서 없이 조회 — 같은 ts에서 id 오름차순 정렬상 "art-1"이 "recruit-1"보다 앞선다.
        when(artworkRepo.search(argThat(q -> q != null && q.cursor() == null)))
                .thenReturn(new ArtworkSearchResult(List.of(artworkDoc), null, false, 1));
        when(recruitRepo.search(argThat(q -> q != null && q.cursor() == null)))
                .thenReturn(new RecruitSearchResult(List.of(recruitDoc), null, false, 1));

        SearchPage<SearchResultItem> firstPage = service.search(mergedQuery(null, 1));

        assertThat(firstPage.items()).extracting(SearchResultItem::id).containsExactly("art-1");
        assertThat(firstPage.hasNext()).isTrue();

        String artworkSubCursor = SearchCursor.encode(List.of(COLLIDING_MILLIS, "art-1"));
        MergedSearchCursor.SubCursors afterPage1 = MergedSearchCursor.decode(firstPage.nextCursor());
        assertThat(afterPage1.artworkCursor()).isEqualTo(artworkSubCursor);
        // recruit는 조회는 됐지만 size 초과로 이번 페이지 응답에 담기지 못했으므로 서브커서가 그대로 null이어야 한다
        assertThat(afterPage1.recruitCursor()).isNull();

        // 2페이지: artwork는 자기 서브커서로, recruit는 여전히 커서 없이(=처음부터) 조회돼야 한다 —
        // 공유 커서였다면 artwork의 id("art-1")가 recruit 조회의 search_after로 그대로 쓰여
        // 사전순으로 뒤인 "recruit-1"이 걸러졌을 것이다(재조회 시 두 소스 모두에 적용되는 기존 버그).
        when(artworkRepo.search(argThat(q -> q != null && artworkSubCursor.equals(q.cursor()))))
                .thenReturn(new ArtworkSearchResult(List.of(), null, false, 1));

        SearchPage<SearchResultItem> secondPage = service.search(mergedQuery(firstPage.nextCursor(), 1));

        assertThat(secondPage.items()).extracting(SearchResultItem::id).containsExactly("recruit-1");
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void 두_소스_모두_결과를_내면_각자_마지막_항목_위치로_서브커서가_갱신된다() {
        ArtworkSearchDocument artworkDoc = artworkDoc("art-1", COLLIDING_MILLIS);
        RecruitSearchDocument recruitDoc = recruitDoc("recruit-1", COLLIDING_MILLIS - 1000);

        when(artworkRepo.search(argThat(q -> q != null && q.cursor() == null)))
                .thenReturn(new ArtworkSearchResult(List.of(artworkDoc), null, false, 1));
        when(recruitRepo.search(argThat(q -> q != null && q.cursor() == null)))
                .thenReturn(new RecruitSearchResult(List.of(recruitDoc), null, false, 1));

        SearchPage<SearchResultItem> page = service.search(mergedQuery(null, 2));

        assertThat(page.items()).extracting(SearchResultItem::id).containsExactly("art-1", "recruit-1");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    private SearchQuery mergedQuery(String cursor, int size) {
        return new SearchQuery("token", null, null, null, null, null, null, null, null, null, cursor, size);
    }

    private ArtworkSearchDocument artworkDoc(String id, long createdAtMillis) {
        return new ArtworkSearchDocument(id, "title", "desc", List.of(), "author-1", "작가", "handle",
                null, null, null, java.util.List.of(), List.of(), List.of(), List.of(), null, null,
                Instant.ofEpochMilli(createdAtMillis), Instant.ofEpochMilli(createdAtMillis));
    }

    private RecruitSearchDocument recruitDoc(String id, long createdAtMillis) {
        return new RecruitSearchDocument(id, PostType.JOB_POSTING.name(), "title", List.of(), List.of(),
                "author-2", "작가2", null, "PUBLISHED",
                Instant.ofEpochMilli(createdAtMillis), Instant.ofEpochMilli(createdAtMillis));
    }
}
