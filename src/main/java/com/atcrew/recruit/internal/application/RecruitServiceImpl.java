package com.atcrew.recruit.internal.application;

import com.atcrew.common.response.CursorPage;
import com.atcrew.recruit.CommunityJobPostingCardInfo;
import com.atcrew.recruit.CommunityTeamRecruitCardInfo;
import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.CreateJobSeekingPostCommand;
import com.atcrew.recruit.CreateTeamPostingCommand;
import com.atcrew.recruit.JobPostingInfo;
import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.JobSeekingPostInfo;
import com.atcrew.recruit.RecruitService;
import com.atcrew.recruit.TeamPostingInfo;
import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.UpdateJobPostingCommand;
import com.atcrew.recruit.UpdateJobSeekingPostCommand;
import com.atcrew.recruit.UpdateTeamPostingCommand;
import com.atcrew.recruit.internal.domain.JobPosting;
import com.atcrew.recruit.internal.domain.TeamPosting;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import com.atcrew.recruit.internal.persistence.JobPostingRepository;
import com.atcrew.recruit.internal.persistence.TeamPostingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
class RecruitServiceImpl implements RecruitService {

    private final JobPostingRepository jobPostingRepository;
    private final TeamPostingRepository teamPostingRepository;
    private final JobSeekingPostService jobSeekingPostService;
    private final AuthorNameResolver authorNameResolver;

    RecruitServiceImpl(JobPostingRepository jobPostingRepository, TeamPostingRepository teamPostingRepository,
            JobSeekingPostService jobSeekingPostService, AuthorNameResolver authorNameResolver) {
        this.jobPostingRepository = jobPostingRepository;
        this.teamPostingRepository = teamPostingRepository;
        this.jobSeekingPostService = jobSeekingPostService;
        this.authorNameResolver = authorNameResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<CommunityJobPostingCardInfo> getJobPostingFeed(String cursor, int size) {
        List<JobPosting> postings = findPublished(cursor, size);
        return toCardPage(postings, size);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<CommunityTeamRecruitCardInfo> getTeamRecruitFeed(String cursor, int size) {
        List<TeamPosting> postings = findPublishedTeamPostings(cursor, size);
        return toTeamCardPage(postings, size);
    }

    @Override
    public JobPostingInfo createJobPosting(String memberId, CreateJobPostingCommand command) {
        JobPosting jobPosting = JobPosting.create(memberId, command);
        if (command.submit()) {
            jobPosting.submitForApproval();
        }
        JobPosting saved = jobPostingRepository.save(jobPosting);
        return toInfo(saved);
    }

    @Override
    public JobPostingInfo updateJobPosting(String memberId, String jobPostingId, UpdateJobPostingCommand command) {
        JobPosting jobPosting = getOwned(jobPostingId, memberId);
        if (jobPosting.getStatus() == JobPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.JOB_POSTING_NOT_FOUND, jobPostingId);
        }
        jobPosting.updateContent(command);
        return toInfo(jobPosting); // 트랜잭션 커밋 시점 dirty checking — 명시적 save() 불필요
    }

    @Override
    public JobPostingInfo submitJobPosting(String memberId, String jobPostingId) {
        JobPosting jobPosting = getOwned(jobPostingId, memberId);
        jobPosting.submitForApproval();
        return toInfo(jobPosting);
    }

    @Override
    public JobPostingInfo closeJobPosting(String memberId, String jobPostingId) {
        JobPosting jobPosting = getOwned(jobPostingId, memberId);
        jobPosting.close();
        return toInfo(jobPosting);
    }

    @Override
    public void deleteJobPosting(String memberId, String jobPostingId) {
        JobPosting jobPosting = getOwned(jobPostingId, memberId);
        jobPosting.moveToTrash();
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<JobPostingInfo> getTrashedJobPostings(String memberId, String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<JobPosting> postings = cursor == null
                ? jobPostingRepository.findByAuthorMemberIdAndStatusOrderByIdDesc(
                        memberId, JobPostingStatus.DELETED, pageable)
                : jobPostingRepository.findByAuthorMemberIdAndStatusAndIdLessThanOrderByIdDesc(
                        memberId, JobPostingStatus.DELETED, cursor, pageable);
        return toInfoPage(postings, size);
    }

    @Override
    public JobPostingInfo restoreJobPosting(String memberId, String jobPostingId) {
        JobPosting jobPosting = getOwned(jobPostingId, memberId);
        jobPosting.restore();
        return toInfo(jobPosting);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<JobPostingInfo> getMyJobPostings(String memberId, String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<JobPosting> postings = cursor == null
                ? jobPostingRepository.findByAuthorMemberIdAndStatusNotOrderByIdDesc(
                        memberId, JobPostingStatus.DELETED, pageable)
                : jobPostingRepository.findByAuthorMemberIdAndStatusNotAndIdLessThanOrderByIdDesc(
                        memberId, JobPostingStatus.DELETED, cursor, pageable);
        return toInfoPage(postings, size);
    }

    @Override
    public JobPostingInfo getJobPosting(String jobPostingId, String viewerMemberId) {
        JobPosting jobPosting = findById(jobPostingId);
        boolean isAuthor = viewerMemberId != null && jobPosting.getAuthorMemberId().equals(viewerMemberId);
        // 공개 노출은 PUBLISHED만 — 작성자 본인은 모든 상태 조회 가능. 그 외에는 존재 자체를 숨기기 위해 404로 통일
        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED && !isAuthor) {
            throw new RecruitException(RecruitErrorCode.JOB_POSTING_NOT_FOUND, jobPostingId);
        }
        if (!isAuthor) {
            jobPostingRepository.incrementViewCount(jobPostingId);
        }
        return toInfo(jobPosting);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<JobPostingInfo> getJobPostings(String cursor, int size) {
        List<JobPosting> postings = findPublished(cursor, size);
        return toInfoPage(postings, size);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<JobPostingInfo> getPendingJobPostings(String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<JobPosting> postings = cursor == null
                ? jobPostingRepository.findByStatusOrderByIdDesc(JobPostingStatus.PENDING, pageable)
                : jobPostingRepository.findByStatusAndIdLessThanOrderByIdDesc(
                        JobPostingStatus.PENDING, cursor, pageable);
        return toInfoPage(postings, size);
    }

    @Override
    public JobPostingInfo approveJobPosting(String jobPostingId) {
        JobPosting jobPosting = findById(jobPostingId);
        jobPosting.approve();
        return toInfo(jobPosting);
    }

    @Override
    public JobPostingInfo rejectJobPosting(String jobPostingId) {
        JobPosting jobPosting = findById(jobPostingId);
        jobPosting.reject();
        return toInfo(jobPosting);
    }

    // === TeamPosting CRUD (§4.2) ===

    @Override
    public TeamPostingInfo createTeamPosting(String memberId, CreateTeamPostingCommand command) {
        TeamPosting teamPosting = TeamPosting.create(memberId, command);
        TeamPosting saved = teamPostingRepository.save(teamPosting);
        return toTeamInfo(saved);
    }

    @Override
    public TeamPostingInfo updateTeamPosting(String memberId, String teamPostingId, UpdateTeamPostingCommand command) {
        TeamPosting teamPosting = getOwnedTeamPosting(teamPostingId, memberId);
        if (teamPosting.getStatus() == TeamPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.TEAM_POSTING_NOT_FOUND, teamPostingId);
        }
        teamPosting.updateContent(command);
        return toTeamInfo(teamPosting); // 트랜잭션 커밋 시점 dirty checking — 명시적 save() 불필요
    }

    @Override
    public TeamPostingInfo closeTeamPosting(String memberId, String teamPostingId) {
        TeamPosting teamPosting = getOwnedTeamPosting(teamPostingId, memberId);
        teamPosting.close();
        return toTeamInfo(teamPosting);
    }

    @Override
    public void deleteTeamPosting(String memberId, String teamPostingId) {
        TeamPosting teamPosting = getOwnedTeamPosting(teamPostingId, memberId);
        teamPosting.moveToTrash();
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<TeamPostingInfo> getTrashedTeamPostings(String memberId, String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<TeamPosting> postings = cursor == null
                ? teamPostingRepository.findByAuthorMemberIdAndStatusOrderByIdDesc(
                        memberId, TeamPostingStatus.DELETED, pageable)
                : teamPostingRepository.findByAuthorMemberIdAndStatusAndIdLessThanOrderByIdDesc(
                        memberId, TeamPostingStatus.DELETED, cursor, pageable);
        return toTeamInfoPage(postings, size);
    }

    @Override
    public TeamPostingInfo restoreTeamPosting(String memberId, String teamPostingId) {
        TeamPosting teamPosting = getOwnedTeamPosting(teamPostingId, memberId);
        teamPosting.restore();
        return toTeamInfo(teamPosting);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<TeamPostingInfo> getMyTeamPostings(String memberId, String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<TeamPosting> postings = cursor == null
                ? teamPostingRepository.findByAuthorMemberIdAndStatusNotOrderByIdDesc(
                        memberId, TeamPostingStatus.DELETED, pageable)
                : teamPostingRepository.findByAuthorMemberIdAndStatusNotAndIdLessThanOrderByIdDesc(
                        memberId, TeamPostingStatus.DELETED, cursor, pageable);
        return toTeamInfoPage(postings, size);
    }

    @Override
    public TeamPostingInfo getTeamPosting(String teamPostingId, String viewerMemberId) {
        TeamPosting teamPosting = findTeamPostingById(teamPostingId);
        boolean isAuthor = viewerMemberId != null && teamPosting.getAuthorMemberId().equals(viewerMemberId);
        // 공개 노출은 PUBLISHED만 — 작성자 본인은 모든 상태 조회 가능. 그 외에는 존재 자체를 숨기기 위해 404로 통일
        if (teamPosting.getStatus() != TeamPostingStatus.PUBLISHED && !isAuthor) {
            throw new RecruitException(RecruitErrorCode.TEAM_POSTING_NOT_FOUND, teamPostingId);
        }
        if (!isAuthor) {
            teamPostingRepository.incrementViewCount(teamPostingId);
        }
        return toTeamInfo(teamPosting);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<TeamPostingInfo> getTeamPostings(String cursor, int size) {
        List<TeamPosting> postings = findPublishedTeamPostings(cursor, size);
        return toTeamInfoPage(postings, size);
    }

    // === JobSeekingPost CRUD (§4.2) — 내부 협력자 JobSeekingPostService에 위임 ===

    @Override
    public JobSeekingPostInfo createJobSeekingPost(String memberId, CreateJobSeekingPostCommand command) {
        return jobSeekingPostService.create(memberId, command);
    }

    @Override
    public JobSeekingPostInfo updateJobSeekingPost(String memberId, String jobSeekingPostId,
            UpdateJobSeekingPostCommand command) {
        return jobSeekingPostService.update(memberId, jobSeekingPostId, command);
    }

    @Override
    public JobSeekingPostInfo publishJobSeekingPost(String memberId, String jobSeekingPostId) {
        return jobSeekingPostService.publish(memberId, jobSeekingPostId);
    }

    @Override
    public JobSeekingPostInfo closeJobSeekingPost(String memberId, String jobSeekingPostId) {
        return jobSeekingPostService.close(memberId, jobSeekingPostId);
    }

    @Override
    public void deleteJobSeekingPost(String memberId, String jobSeekingPostId) {
        jobSeekingPostService.delete(memberId, jobSeekingPostId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<JobSeekingPostInfo> getTrashedJobSeekingPosts(String memberId, String cursor, int size) {
        return jobSeekingPostService.getTrashed(memberId, cursor, size);
    }

    @Override
    public JobSeekingPostInfo restoreJobSeekingPost(String memberId, String jobSeekingPostId) {
        return jobSeekingPostService.restore(memberId, jobSeekingPostId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<JobSeekingPostInfo> getMyJobSeekingPosts(String memberId, String cursor, int size) {
        return jobSeekingPostService.getMine(memberId, cursor, size);
    }

    @Override
    @Transactional(readOnly = true)
    public JobSeekingPostInfo getJobSeekingPost(String jobSeekingPostId, String viewerMemberId) {
        return jobSeekingPostService.get(jobSeekingPostId, viewerMemberId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<JobSeekingPostInfo> getJobSeekingPosts(String cursor, int size) {
        return jobSeekingPostService.getPublished(cursor, size);
    }

    private List<JobPosting> findPublished(String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        return cursor == null
                ? jobPostingRepository.findByStatusOrderByIdDesc(JobPostingStatus.PUBLISHED, pageable)
                : jobPostingRepository.findByStatusAndIdLessThanOrderByIdDesc(
                        JobPostingStatus.PUBLISHED, cursor, pageable);
    }

    private JobPosting getOwned(String jobPostingId, String memberId) {
        JobPosting jobPosting = findById(jobPostingId);
        jobPosting.checkAuthor(memberId);
        return jobPosting;
    }

    private JobPosting findById(String jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new RecruitException(RecruitErrorCode.JOB_POSTING_NOT_FOUND, jobPostingId));
    }

    private CursorPage<JobPostingInfo> toInfoPage(List<JobPosting> postings, int size) {
        if (postings.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = postings.size() > size;
        List<JobPosting> page = hasNext ? postings.subList(0, size) : postings;
        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(JobPosting::getAuthorMemberId).toList());
        List<JobPostingInfo> items = page.stream()
                .map(p -> JobPostingMapper.toInfo(p, authorNames.get(p.getAuthorMemberId())))
                .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }

    private CursorPage<CommunityJobPostingCardInfo> toCardPage(List<JobPosting> postings, int size) {
        if (postings.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = postings.size() > size;
        List<JobPosting> page = hasNext ? postings.subList(0, size) : postings;
        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(JobPosting::getAuthorMemberId).toList());
        List<CommunityJobPostingCardInfo> items = page.stream()
                .map(p -> JobPostingMapper.toCardInfo(p, authorNames.get(p.getAuthorMemberId())))
                .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }

    private JobPostingInfo toInfo(JobPosting jobPosting) {
        return JobPostingMapper.toInfo(jobPosting, authorNameResolver.resolve(jobPosting.getAuthorMemberId()));
    }

    private List<TeamPosting> findPublishedTeamPostings(String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        return cursor == null
                ? teamPostingRepository.findByStatusOrderByIdDesc(TeamPostingStatus.PUBLISHED, pageable)
                : teamPostingRepository.findByStatusAndIdLessThanOrderByIdDesc(
                        TeamPostingStatus.PUBLISHED, cursor, pageable);
    }

    private TeamPosting getOwnedTeamPosting(String teamPostingId, String memberId) {
        TeamPosting teamPosting = findTeamPostingById(teamPostingId);
        teamPosting.checkAuthor(memberId);
        return teamPosting;
    }

    private TeamPosting findTeamPostingById(String teamPostingId) {
        return teamPostingRepository.findById(teamPostingId)
                .orElseThrow(() -> new RecruitException(RecruitErrorCode.TEAM_POSTING_NOT_FOUND, teamPostingId));
    }

    private CursorPage<TeamPostingInfo> toTeamInfoPage(List<TeamPosting> postings, int size) {
        if (postings.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = postings.size() > size;
        List<TeamPosting> page = hasNext ? postings.subList(0, size) : postings;
        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(TeamPosting::getAuthorMemberId).toList());
        List<TeamPostingInfo> items = page.stream()
                .map(p -> TeamPostingMapper.toInfo(p, authorNames.get(p.getAuthorMemberId())))
                .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }

    private CursorPage<CommunityTeamRecruitCardInfo> toTeamCardPage(List<TeamPosting> postings, int size) {
        if (postings.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = postings.size() > size;
        List<TeamPosting> page = hasNext ? postings.subList(0, size) : postings;
        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(TeamPosting::getAuthorMemberId).toList());
        List<CommunityTeamRecruitCardInfo> items = page.stream()
                .map(p -> TeamPostingMapper.toCardInfo(p, authorNames.get(p.getAuthorMemberId())))
                .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }

    private TeamPostingInfo toTeamInfo(TeamPosting teamPosting) {
        return TeamPostingMapper.toInfo(teamPosting, authorNameResolver.resolve(teamPosting.getAuthorMemberId()));
    }
}
