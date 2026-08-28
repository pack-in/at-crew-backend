package com.atcrew.artwork;

/**
 * 커뮤니티 포트폴리오 목록 정렬 기준 — /community 포트폴리오 탭의 정렬 chip(이슈 #78).
 *
 * <p>어떤 기준이든 동일 정렬값이 있을 수 있으므로 실제 정렬은 항상 (정렬 키, 작품 ID) 2단이다.
 * 작품 ID는 UUIDv7이라 생성 시각순과 같은 순서를 갖는 고유 tiebreaker다.
 */
public enum ArtworkSort {
    LATEST,        // 최신순 — 등록일 내림차순(기본값)
    OLDEST,        // 오래된순 — 등록일 오름차순
    VIEW_COUNT,    // 조회순 — 누적 조회수 내림차순
    BOOKMARK_COUNT // 북마크순 — 북마크 수 내림차순
}
