package com.atcrew.search.internal.application;

import com.atcrew.search.RecruitSearchPort;
import com.atcrew.search.SearchPage;
import com.atcrew.search.SearchQuery;
import com.atcrew.search.SearchResultItem;
import org.springframework.stereotype.Component;

/**
 * recruit 모듈이 생기기 전까지 사용하는 임시 구현체 — 항상 빈 결과를 반환한다.
 * recruit 모듈 완성 후 이 클래스를 실제 구현체로 교체(또는 삭제)한다(community의 {@code NoopRecruitFeedPort}와 동일 패턴).
 */
@Component
class NoopRecruitSearchPort implements RecruitSearchPort {

    @Override
    public SearchPage<SearchResultItem> search(SearchQuery query) {
        return SearchPage.empty();
    }
}
