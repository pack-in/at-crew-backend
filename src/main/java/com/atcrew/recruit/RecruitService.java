package com.atcrew.recruit;

import com.atcrew.common.response.CursorPage;

/**
 * recruit 모듈 공개 API (docs/design/recruit-module-design.md §4.1, §4.2, §4.3, §6.1).
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

    // 끌어올리기 적용 — 작성자 본인만 가능, 적용 중(now < boostedUntil)이면 거부(§2.1.1)
    JobPostingInfo boostJobPosting(String memberId, String jobPostingId);

    // === 관리자 (§7 — 별도 Role 체계 도입 전까지 인증된 회원 누구나 호출 가능) ===

    // PENDING 목록(커서)
    CursorPage<JobPostingInfo> getPendingJobPostings(String cursor, int size);

    // 승인 — PENDING → PUBLISHED
    JobPostingInfo approveJobPosting(String jobPostingId);

    // 반려 — PENDING → CLOSED
    JobPostingInfo rejectJobPosting(String jobPostingId);

    // === TeamPosting CRUD (§4.2) — 승인 절차 없음(생성 즉시 PUBLISHED), 관리자 승인 엔드포인트 없음 ===

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

    // 끌어올리기 적용 — JobPosting과 동일 규칙(§2.1.1)
    TeamPostingInfo boostTeamPosting(String memberId, String teamPostingId);

    // === JobSeekingPost CRUD (§4.2) — 승인 절차 없음, JobPosting과 대칭 구조 ===

    // 작성 — command.publish()=true면 저장 직후 PUBLISHED로 게시
    JobSeekingPostInfo createJobSeekingPost(String memberId, CreateJobSeekingPostCommand command);

    // 수정 — 작성자 본인만 가능, 휴지통에 있는 구직글은 수정 불가
    JobSeekingPostInfo updateJobSeekingPost(String memberId, String jobSeekingPostId, UpdateJobSeekingPostCommand command);

    // 게시 — DRAFT/CLOSED → PUBLISHED
    JobSeekingPostInfo publishJobSeekingPost(String memberId, String jobSeekingPostId);

    // 마감 처리 — PUBLISHED → CLOSED
    JobSeekingPostInfo closeJobSeekingPost(String memberId, String jobSeekingPostId);

    // 휴지통 이동 (soft delete)
    void deleteJobSeekingPost(String memberId, String jobSeekingPostId);

    // 휴지통 목록 — 작성자 본인
    CursorPage<JobSeekingPostInfo> getTrashedJobSeekingPosts(String memberId, String cursor, int size);

    // 휴지통 복구 — DELETED → DRAFT
    JobSeekingPostInfo restoreJobSeekingPost(String memberId, String jobSeekingPostId);

    // 내 목록 — DELETED를 제외한 모든 상태
    CursorPage<JobSeekingPostInfo> getMyJobSeekingPosts(String memberId, String cursor, int size);

    // 상세 조회 — viewerMemberId가 작성자 본인이면 모든 상태 조회 가능, 그 외에는 PUBLISHED만 공개
    JobSeekingPostInfo getJobSeekingPost(String jobSeekingPostId, String viewerMemberId);

    // 공개 목록(커서) — PUBLISHED만
    CursorPage<JobSeekingPostInfo> getJobSeekingPosts(String cursor, int size);

    // === 지원/지원자 관리 (§2.4, §2.5, §2.6) — 목록·상태변경·삭제는 Job/Team 모두 작성자 소유권 검증 ===

    // 구인글 지원 — PUBLISHED 상태에만 가능, 중복 지원은 DB 유니크 제약으로 차단
    ApplicationInfo applyToJobPosting(String memberId, String jobPostingId, CreateApplicationCommand command);

    // 구인글 지원자 목록(커서) — 작성자 본인만 조회 가능
    CursorPage<ApplicationInfo> getJobPostingApplications(String memberId, String jobPostingId, String cursor, int size);

    // 구인글 지원자 채용 단계 변경 — 작성자 본인만 가능
    ApplicationInfo updateJobApplicationReviewStatus(String memberId, String jobPostingId, String applicationId,
            ApplicationReviewStatus reviewStatus);

    // 구인글 지원 내역 삭제 — 작성자 본인만 가능
    void deleteJobApplication(String memberId, String jobPostingId, String applicationId);

    // 팀원모집글 지원 — PUBLISHED 상태에만 가능, 중복 지원은 DB 유니크 제약으로 차단
    ApplicationInfo applyToTeamPosting(String memberId, String teamPostingId, CreateApplicationCommand command);

    // 팀원모집글 지원자 목록(커서) — 작성자 본인만 조회 가능
    CursorPage<ApplicationInfo> getTeamPostingApplications(String memberId, String teamPostingId, String cursor, int size);

    // 팀원모집글 지원자 채용 단계 변경 — 작성자 본인만 가능
    ApplicationInfo updateTeamApplicationReviewStatus(String memberId, String teamPostingId, String applicationId,
            ApplicationReviewStatus reviewStatus);

    // 팀원모집글 지원 내역 삭제 — 작성자 본인만 가능
    void deleteTeamApplication(String memberId, String teamPostingId, String applicationId);

    // === 관심 작가 (§2.7, §4.3) — 기업 계정 전용이나 기업 인증 게이팅은 아직 스텁(§7) ===

    // 좋아요 저장 — 이미 저장돼 있으면 최초 저장 시각을 유지
    void likeArtist(String companyMemberId, String artistMemberId);

    // 좋아요 해제 — 저장돼 있지 않아도 성공(멱등)
    void unlikeArtist(String companyMemberId, String artistMemberId);

    // 좋아요한 작가 목록(커서) — 저장 시각 내림차순
    CursorPage<LikedArtistInfo> getLikedArtists(String companyMemberId, String cursor, int size);

    // 작가 마이페이지 조회 기록 — 같은 작가 재조회 시 조회 시각만 갱신
    void recordArtistView(String companyMemberId, String artistMemberId);

    // 최근 본 작가 목록(커서) — 조회 시각 내림차순
    CursorPage<RecentlyViewedArtistInfo> getRecentlyViewedArtists(String companyMemberId, String cursor, int size);
}
