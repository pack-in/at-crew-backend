package com.atcrew.recruit;

/**
 * 구인글/팀원모집글/구직글의 색인 대상 상태(공개 여부·내용)가 바뀔 수 있는 모든 시점에 발행되는 이벤트.
 *
 * <p>색인/제거 판단은 수신 측(search 모듈)이 {@link RecruitService#getPostForIndexing(RecruitPostType, String)}로
 * 재조회해서 내린다 — artwork의 {@code ArtworkChangedEvent}와 동일하게 재조회 기반이라 페이로드를 얇게 유지해
 * 결합을 최소화하고, 중복 발행·순서 뒤바뀜에도 안전(멱등)하다 (docs/design/search-module-design.md §5.1).
 */
public record RecruitPostChangedEvent(String postId, RecruitPostType postType) {
}
