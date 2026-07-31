package com.atcrew.recruit;

import com.atcrew.common.response.CursorPage;

/**
 * recruit 모듈 공개 API (docs/design/recruit-module-design.md §4.1, §4.2, §6.1).
 *
 * <p>JobSeekingPost/Application 관련 메서드는 다음 단계에서 추가된다.
 */
public interface RecruitService {

    // 커뮤니티 "구인글" 탭 피드 — PUBLISHED 상태만 커서 페이지네이션으로 조회
    CursorPage<CommunityJobPostingCardInfo> getJobPostingFeed(String cursor, int size);

    // 커뮤니티 "팀원모집글" 탭 피드 — PUBLISHED 상태만 커서 페이지네이션으로 조회
    CursorPage<CommunityTeamRecruitCardInfo> getTeamRecruitFeed(String cursor, int size);

    // === JobPosting CRUD (§4.1) ===

    // 작성 — command.submit()=true면 저장 직후 PENDING 제출까지 처리
    JobPostingInfo createJobPosting(String memberId, CreateJobPostingCommand command);

    // 수정 — 작성자 본인만 가능, 휴지통에 있는 구인글은 수정 불가
    JobPostingInfo updateJobPosting(String memberId, String jobPostingId, UpdateJobPostingCommand command);

    // 제출 — DRAFT/PENDING → PENDING
    JobPostingInfo submitJobPosting(String memberId, String jobPostingId);

    // 마감 처리 — PUBLISHED/PENDING → CLOSED
    JobPostingInfo closeJobPosting(String memberId, String jobPostingId);

    // 휴지통 이동 (soft delete)
    void deleteJobPosting(String memberId, String jobPostingId);

    // 휴지통 목록 — 작성자 본인
    CursorPage<JobPostingInfo> getTrashedJobPostings(String memberId, String cursor, int size);

    // 휴지통 복구 — DELETED → DRAFT
    JobPostingInfo restoreJobPosting(String memberId, String jobPostingId);

    // 내 목록 — DELETED를 제외한 모든 상태
    CursorPage<JobPostingInfo> getMyJobPostings(String memberId, String cursor, int size);

    // 상세 조회 — viewerMemberId가 작성자 본인이면 모든 상태 조회 가능, 그 외에는 PUBLISHED만 공개
    JobPostingInfo getJobPosting(String jobPostingId, String viewerMemberId);

    // 공개 목록(커서) — PUBLISHED만
    CursorPage<JobPostingInfo> getJobPostings(String cursor, int size);

    // === 관리자 (§7 — 별도 Role 체계 도입 전까지 인증된 회원 누구나 호출 가능) ===

    // PENDING 목록(커서)
    CursorPage<JobPostingInfo> getPendingJobPostings(String cursor, int size);

    // 승인 — PENDING → PUBLISHED
    JobPostingInfo approveJobPosting(String jobPostingId);

    // 반려 — PENDING → CLOSED
    JobPostingInfo rejectJobPosting(String jobPostingId);

    // === TeamPosting CRUD (§4.2) — 승인 절차 없음(생성 즉시 PUBLISHED), 부스트/관리자 승인 엔드포인트 없음 ===

    // 작성 — 승인 절차 없이 저장 즉시 PUBLISHED로 게시
    TeamPostingInfo createTeamPosting(String memberId, CreateTeamPostingCommand command);

    // 수정 — 작성자 본인만 가능, 휴지통에 있는 팀원모집글은 수정 불가
    TeamPostingInfo updateTeamPosting(String memberId, String teamPostingId, UpdateTeamPostingCommand command);

    // 마감 처리 — PUBLISHED → CLOSED
    TeamPostingInfo closeTeamPosting(String memberId, String teamPostingId);

    // 휴지통 이동 (soft delete)
    void deleteTeamPosting(String memberId, String teamPostingId);

    // 휴지통 목록 — 작성자 본인
    CursorPage<TeamPostingInfo> getTrashedTeamPostings(String memberId, String cursor, int size);

    // 휴지통 복구 — DELETED → PUBLISHED (승인 절차가 없으므로 즉시 재게시)
    TeamPostingInfo restoreTeamPosting(String memberId, String teamPostingId);

    // 내 목록 — DELETED를 제외한 모든 상태
    CursorPage<TeamPostingInfo> getMyTeamPostings(String memberId, String cursor, int size);

    // 상세 조회 — viewerMemberId가 작성자 본인이면 모든 상태 조회 가능, 그 외에는 PUBLISHED만 공개
    TeamPostingInfo getTeamPosting(String teamPostingId, String viewerMemberId);

    // 공개 목록(커서) — PUBLISHED만
    CursorPage<TeamPostingInfo> getTeamPostings(String cursor, int size);
}
