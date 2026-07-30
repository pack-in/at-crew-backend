package com.atcrew.community.internal.application;

import com.atcrew.common.response.CursorPage;
import com.atcrew.community.CommunityJobPostingCardInfo;
import com.atcrew.community.CommunityTeamRecruitCardInfo;
import com.atcrew.community.RecruitFeedPort;
import org.springframework.stereotype.Component;

/**
 * recruit 모듈이 생기기 전까지 사용하는 임시 구현체 — 항상 빈 목록을 반환한다.
 * recruit 모듈 완성 후 이 클래스를 실제 구현체로 교체(또는 삭제)한다.
 */
@Component
class NoopRecruitFeedPort implements RecruitFeedPort {

    @Override
    public CursorPage<CommunityJobPostingCardInfo> getJobPostingFeed(String cursor, int size) {
        return CursorPage.empty();
    }

    @Override
    public CursorPage<CommunityTeamRecruitCardInfo> getTeamRecruitFeed(String cursor, int size) {
        return CursorPage.empty();
    }
}
