package com.atcrew.search.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.search.PostType;
import com.atcrew.search.RecruitSearchPort;
import com.atcrew.search.SearchPage;
import com.atcrew.search.SearchQuery;
import com.atcrew.search.SearchResultItem;
import com.atcrew.search.SearchService;
import com.atcrew.search.internal.domain.ArtworkSearchDocument;
import com.atcrew.search.internal.persistence.ArtworkSearchQueryRepository;
import com.atcrew.search.internal.persistence.ArtworkSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class SearchServiceImpl implements SearchService {

    private final ArtworkSearchQueryRepository artworkSearchQueryRepository;
    private final RecruitSearchPort recruitSearchPort;

    SearchServiceImpl(ArtworkSearchQueryRepository artworkSearchQueryRepository,
                       RecruitSearchPort recruitSearchPort) {
        this.artworkSearchQueryRepository = artworkSearchQueryRepository;
        this.recruitSearchPort = recruitSearchPort;
    }

    @Override
    public SearchPage<SearchResultItem> search(SearchQuery query) {
        if (query.isEmptyCriteria()) {
            // 피그마 "최초 진입 시 결과 미노출" 규칙 (docs/design/search-module-design.md §1.5)
            return SearchPage.empty();
        }

        if (!query.includesPortfolio()) {
            // postTypes가 recruit 소유 유형만 지정된 경우 — 전량 위임
            return recruitSearchPort.search(query);
        }

        ArtworkSearchResult artworkResult = artworkSearchQueryRepository.search(query);
        List<SearchResultItem> items = artworkResult.items().stream()
                .map(this::toResultItem)
                .toList();

        long totalCount = artworkResult.totalCount();

        // recruit 소유 유형도 함께 요청됐다면 총 건수만 합산한다. RecruitSearchPort가 실구현되기 전까지는
        // 항상 빈 결과라 커서 병합이 필요 없다 — recruit 모듈 완성 후 다중 소스 커서 병합 재설계 필요
        // (docs/design/search-module-design.md §8 미결 사항).
        if (query.includesRecruitOwned()) {
            totalCount += recruitSearchPort.search(query).totalCount();
        }

        return new SearchPage<>(items, artworkResult.nextCursor(), artworkResult.hasNext(), totalCount);
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
}
