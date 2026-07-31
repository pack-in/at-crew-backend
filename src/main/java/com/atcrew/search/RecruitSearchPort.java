package com.atcrew.search;

/**
 * 구인글/구직글/팀원모집글 검색 조회 포트.
 *
 * <p>recruit 모듈이 아직 구현되지 않아, search 모듈이 임시로 이 인터페이스를 소유한다.
 * recruit 모듈이 생기면 해당 모듈이 구현체를 제공하도록 이관한다
 * (docs/design/search-module-design.md §1.2, community 모듈의 {@code RecruitFeedPort}와 동일한 이관 경로).
 * 그 전까지는 {@code NoopRecruitSearchPort}가 항상 빈 결과를 반환한다.
 */
public interface RecruitSearchPort {

    SearchPage<SearchResultItem> search(SearchQuery query);
}
