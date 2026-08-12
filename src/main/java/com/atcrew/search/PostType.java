package com.atcrew.search;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검색 결과의 게시글 유형. 피그마 UI개편_검색(5154:41768) "게시글 유형" 필터 4종에 대응한다.
 *
 * <p>{@code JOB_POSTING}/{@code JOB_SEEKING}/{@code TEAM_RECRUIT}는 recruit 모듈 소유 데이터로,
 * 상수 이름이 {@code com.atcrew.recruit.RecruitPostType}과 1:1로 대응한다(서비스 계층에서 이름으로 변환).
 */
@Schema(description = """
        검색 대상 게시글 유형 — 검색 결과 카드가 어느 도메인의 글인지 나타내며, postTypes 필터 값으로도 쓴다.
        - PORTFOLIO: 포트폴리오(작품). 작품 분야·창작 유형·연령대·소재 대상 필터가 적용되는 유일한 유형이다.
        - JOB_POSTING: 구인글.
        - JOB_SEEKING: 구직글.
        - TEAM_RECRUIT: 팀원모집글.
        구인글·구직글·팀원모집글에는 연령 등급·작성자 핸들·성인 썸네일이 없어 결과 카드에서 항상 null이다.""",
        example = "PORTFOLIO")
public enum PostType {
    PORTFOLIO,     // 포트폴리오 (artwork 모듈 소유)
    JOB_POSTING,   // 구인글 (recruit 모듈 소유)
    JOB_SEEKING,   // 구직글 (recruit 모듈 소유)
    TEAM_RECRUIT   // 팀원모집글 (recruit 모듈 소유)
}
