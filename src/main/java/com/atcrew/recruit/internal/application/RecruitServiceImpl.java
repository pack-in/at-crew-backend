package com.atcrew.recruit.internal.application;

import com.atcrew.common.response.CursorPage;
import com.atcrew.member.MemberService;
import com.atcrew.recruit.CommunityJobPostingCardInfo;
import com.atcrew.recruit.CommunityTeamRecruitCardInfo;
import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.CreateTeamPostingCommand;
import com.atcrew.recruit.JobPostingInfo;
import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.RecruitService;
import com.atcrew.recruit.TeamPostingInfo;
import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.UpdateJobPostingCommand;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
class RecruitServiceImpl implements RecruitService {

    private final JobPostingRepository jobPostingRepository;
    private final TeamPostingRepository teamPostingRepository;
    private final MemberService memberService;

    RecruitServiceImpl(JobPostingRepository jobPostingRepository, TeamPostingRepository teamPostingRepository,
            MemberService memberService) {
        this.jobPostingRepository = jobPostingRepository;
        this.teamPostingRepository = teamPostingRepository;
        this.memberService = memberService;
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
        Map<String, String> authorNames = resolveAuthorNames(page);
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
        Map<String, String> authorNames = resolveAuthorNames(page);
        List<CommunityJobPostingCardInfo> items = page.stream()
                .map(p -> JobPostingMapper.toCardInfo(p, authorNames.get(p.getAuthorMemberId())))
                .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }

    private JobPostingInfo toInfo(JobPosting jobPosting) {
        return JobPostingMapper.toInfo(jobPosting, resolveAuthorName(jobPosting.getAuthorMemberId()));
    }

    // 페이지 내 중복 작성자에 대한 반복 조회를 피하기 위해 고유 작성자 ID만 일괄 조회한다(N+1 완화).
    // resolveAuthorName()이 null을 반환할 수 있어 Collectors.toMap 대신 HashMap에 직접 채운다(toMap은 null 값을 허용하지 않음).
    private Map<String, String> resolveAuthorNames(List<JobPosting> postings) {
        Set<String> authorIds = postings.stream().map(JobPosting::getAuthorMemberId).collect(Collectors.toSet());
        Map<String, String> authorNames = new HashMap<>();
        authorIds.forEach(id -> authorNames.put(id, resolveAuthorName(id)));
        return authorNames;
    }

    // 작성자 표시명 조회 실패(탈퇴 등) 시 응답 자체를 막지 않고 null로 대체
    private String resolveAuthorName(String authorMemberId) {
        try {
            return memberService.findById(authorMemberId).name();
        } catch (RuntimeException e) {
            return null;
        }
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
        Map<String, String> authorNames = resolveTeamAuthorNames(page);
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
        Map<String, String> authorNames = resolveTeamAuthorNames(page);
        List<CommunityTeamRecruitCardInfo> items = page.stream()
                .map(p -> TeamPostingMapper.toCardInfo(p, authorNames.get(p.getAuthorMemberId())))
                .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }

    private TeamPostingInfo toTeamInfo(TeamPosting teamPosting) {
        return TeamPostingMapper.toInfo(teamPosting, resolveAuthorName(teamPosting.getAuthorMemberId()));
    }

    // 페이지 내 중복 작성자에 대한 반복 조회를 피하기 위해 고유 작성자 ID만 일괄 조회한다(N+1 완화).
    private Map<String, String> resolveTeamAuthorNames(List<TeamPosting> postings) {
        Set<String> authorIds = postings.stream().map(TeamPosting::getAuthorMemberId).collect(Collectors.toSet());
        Map<String, String> authorNames = new HashMap<>();
        authorIds.forEach(id -> authorNames.put(id, resolveAuthorName(id)));
        return authorNames;
    }
}
