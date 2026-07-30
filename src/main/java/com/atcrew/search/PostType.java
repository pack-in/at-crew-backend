package com.atcrew.search;

/**
 * 검색 결과의 게시글 유형. 피그마 UI개편_검색(5154:41768) "게시글 유형" 필터 4종에 대응한다.
 *
 * <p>{@code JOB_POSTING}/{@code JOB_SEEKING}/{@code TEAM_RECRUIT}는 recruit 모듈 소유 데이터로,
 * recruit 모듈이 없는 동안은 {@link RecruitSearchPort}를 통해 항상 빈 결과로 처리된다.
 */
public enum PostType {
    PORTFOLIO,     // 포트폴리오 (artwork 모듈 소유)
    JOB_POSTING,   // 구인글 (recruit 모듈 소유 — 미구현)
    JOB_SEEKING,   // 구직글 (recruit 모듈 소유 — 미구현)
    TEAM_RECRUIT   // 팀원모집글 (recruit 모듈 소유 — 미구현)
}
