package com.atcrew.community;

import com.atcrew.common.response.CursorPage;

/**
 * 구인글/팀원모집글 탭 데이터 조회 포트.
 *
 * <p>recruit 모듈이 아직 구현되지 않아, community 모듈이 임시로 이 인터페이스를 소유한다.
 * recruit 모듈이 생기면 해당 모듈이 구현체를 제공하도록 이관한다
 * (docs/design/community-module-design.md §7.3 참고). 그 전까지는 {@code NoopRecruitFeedPort}가
 * 항상 빈 목록을 반환한다.
 */
public interface RecruitFeedPort {

    CursorPage<CommunityJobPostingCardInfo> getJobPostingFeed(String cursor, int size);

    CursorPage<CommunityTeamRecruitCardInfo> getTeamRecruitFeed(String cursor, int size);
}
