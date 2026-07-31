package com.atcrew.recruit;

/**
 * 검색 대상이 되는 recruit 소유 게시글 유형 (docs/design/recruit-module-design.md §2.1~§2.3).
 * search 모듈의 게시글 유형 필터 중 recruit이 소유하는 3종에 대응한다.
 */
public enum RecruitPostType {
    JOB_POSTING,  // 구인글
    JOB_SEEKING,  // 구직글
    TEAM_RECRUIT  // 팀원모집글
}
