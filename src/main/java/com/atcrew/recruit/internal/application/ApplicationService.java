package com.atcrew.recruit.internal.application;

import com.atcrew.common.response.CursorPage;
import com.atcrew.recruit.ApplicationInfo;
import com.atcrew.recruit.ApplicationReviewStatus;
import com.atcrew.recruit.CreateApplicationCommand;
import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.internal.domain.JobApplication;
import com.atcrew.recruit.internal.domain.JobPosting;
import com.atcrew.recruit.internal.domain.TeamApplication;
import com.atcrew.recruit.internal.domain.TeamPosting;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import com.atcrew.recruit.internal.persistence.JobApplicationRepository;
import com.atcrew.recruit.internal.persistence.JobPostingRepository;
import com.atcrew.recruit.internal.persistence.TeamApplicationRepository;
import com.atcrew.recruit.internal.persistence.TeamPostingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 지원 접수 및 지원자 관리 (docs/design/recruit-module-design.md §2.4, §2.5, §2.6).
 *
 * <p>지원자 목록·상태 변경·삭제는 구인글/팀원모집글 <b>양쪽 모두</b> 작성자 소유권을 검증한다 —
 * laiteu에서 TeamPosting 쪽 검증만 주석 처리돼 있던 취약점을 재현하지 않기 위한 대칭 구현이다(§10-2).
 */
@Service
@Transactional
class ApplicationService {

    private final JobPostingRepository jobPostingRepository;
    private final TeamPostingRepository teamPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final TeamApplicationRepository teamApplicationRepository;
    private final AuthorNameResolver authorNameResolver;

    ApplicationService(JobPostingRepository jobPostingRepository, TeamPostingRepository teamPostingRepository,
            JobApplicationRepository jobApplicationRepository, TeamApplicationRepository teamApplicationRepository,
            AuthorNameResolver authorNameResolver) {
        this.jobPostingRepository = jobPostingRepository;
        this.teamPostingRepository = teamPostingRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.teamApplicationRepository = teamApplicationRepository;
        this.authorNameResolver = authorNameResolver;
    }

    // === 구인글 지원 ===

    ApplicationInfo applyToJobPosting(String memberId, String jobPostingId, CreateApplicationCommand command) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            // 미게시·마감·휴지통 상태의 글에는 지원을 받지 않는다
            throw new RecruitException(RecruitErrorCode.APPLICATION_NOT_ALLOWED, "지원 불가 상태: " + jobPosting.getStatus());
        }
        if (jobPosting.getAuthorMemberId().equals(memberId)) {
            throw new RecruitException(RecruitErrorCode.SELF_APPLICATION_NOT_ALLOWED);
        }
        JobApplication application = JobApplication.create(jobPostingId, memberId, command);
        try {
            // 중복 지원 방지는 유니크 제약이 담당한다 — 애플리케이션 선체크(check-then-act)는 동시 요청에 취약(§10-3).
            // 제약 위반을 이 지점에서 잡으려면 flush가 필요하다.
            jobApplicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            throw new RecruitException(RecruitErrorCode.DUPLICATE_APPLICATION, "jobPostingId=" + jobPostingId);
        }
        return ApplicationMapper.toInfo(application, authorNameResolver.resolve(memberId));
    }

    @Transactional(readOnly = true)
    CursorPage<ApplicationInfo> getJobPostingApplications(String memberId, String jobPostingId, String cursor, int size) {
        findJobPosting(jobPostingId).checkAuthor(memberId); // 소유권 검증(§2.6)
        Pageable pageable = PageRequest.of(0, size + 1);
        List<JobApplication> applications = cursor == null
                ? jobApplicationRepository.findByJobPostingIdOrderByIdDesc(jobPostingId, pageable)
                : jobApplicationRepository.findByJobPostingIdAndIdLessThanOrderByIdDesc(jobPostingId, cursor, pageable);
        if (applications.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = applications.size() > size;
        List<JobApplication> page = hasNext ? applications.subList(0, size) : applications;
        Map<String, String> applicantNames = authorNameResolver.resolveAll(
                page.stream().map(JobApplication::getApplicantMemberId).toList());
        List<ApplicationInfo> items = page.stream()
                .map(a -> ApplicationMapper.toInfo(a, applicantNames.get(a.getApplicantMemberId())))
                .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }

    ApplicationInfo updateJobApplicationReviewStatus(String memberId, String jobPostingId, String applicationId,
            ApplicationReviewStatus reviewStatus) {
        findJobPosting(jobPostingId).checkAuthor(memberId); // 소유권 검증(§2.6)
        JobApplication application = jobApplicationRepository.findByIdAndJobPostingId(applicationId, jobPostingId)
                .orElseThrow(() -> new RecruitException(RecruitErrorCode.APPLICATION_NOT_FOUND, applicationId));
        application.changeReviewStatus(reviewStatus);
        // 트랜잭션 커밋 시점 dirty checking — 명시적 save() 불필요
        return ApplicationMapper.toInfo(application, authorNameResolver.resolve(application.getApplicantMemberId()));
    }

    void deleteJobApplication(String memberId, String jobPostingId, String applicationId) {
        findJobPosting(jobPostingId).checkAuthor(memberId); // 소유권 검증(§2.6)
        JobApplication application = jobApplicationRepository.findByIdAndJobPostingId(applicationId, jobPostingId)
                .orElseThrow(() -> new RecruitException(RecruitErrorCode.APPLICATION_NOT_FOUND, applicationId));
        jobApplicationRepository.delete(application);
    }

    // === 팀원모집글 지원 ===

    ApplicationInfo applyToTeamPosting(String memberId, String teamPostingId, CreateApplicationCommand command) {
        TeamPosting teamPosting = findTeamPosting(teamPostingId);
        if (teamPosting.getStatus() != TeamPostingStatus.PUBLISHED) {
            throw new RecruitException(RecruitErrorCode.APPLICATION_NOT_ALLOWED, "지원 불가 상태: " + teamPosting.getStatus());
        }
        if (teamPosting.getAuthorMemberId().equals(memberId)) {
            throw new RecruitException(RecruitErrorCode.SELF_APPLICATION_NOT_ALLOWED);
        }
        TeamApplication application = TeamApplication.create(teamPostingId, memberId, command);
        try {
            teamApplicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            throw new RecruitException(RecruitErrorCode.DUPLICATE_APPLICATION, "teamPostingId=" + teamPostingId);
        }
        return ApplicationMapper.toInfo(application, authorNameResolver.resolve(memberId));
    }

    @Transactional(readOnly = true)
    CursorPage<ApplicationInfo> getTeamPostingApplications(String memberId, String teamPostingId, String cursor, int size) {
        findTeamPosting(teamPostingId).checkAuthor(memberId); // 소유권 검증(§2.6) — laiteu에서 누락됐던 지점
        Pageable pageable = PageRequest.of(0, size + 1);
        List<TeamApplication> applications = cursor == null
                ? teamApplicationRepository.findByTeamPostingIdOrderByIdDesc(teamPostingId, pageable)
                : teamApplicationRepository.findByTeamPostingIdAndIdLessThanOrderByIdDesc(teamPostingId, cursor, pageable);
        if (applications.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = applications.size() > size;
        List<TeamApplication> page = hasNext ? applications.subList(0, size) : applications;
        Map<String, String> applicantNames = authorNameResolver.resolveAll(
                page.stream().map(TeamApplication::getApplicantMemberId).toList());
        List<ApplicationInfo> items = page.stream()
                .map(a -> ApplicationMapper.toInfo(a, applicantNames.get(a.getApplicantMemberId())))
                .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }

    ApplicationInfo updateTeamApplicationReviewStatus(String memberId, String teamPostingId, String applicationId,
            ApplicationReviewStatus reviewStatus) {
        findTeamPosting(teamPostingId).checkAuthor(memberId); // 소유권 검증(§2.6)
        TeamApplication application = teamApplicationRepository.findByIdAndTeamPostingId(applicationId, teamPostingId)
                .orElseThrow(() -> new RecruitException(RecruitErrorCode.APPLICATION_NOT_FOUND, applicationId));
        application.changeReviewStatus(reviewStatus);
        return ApplicationMapper.toInfo(application, authorNameResolver.resolve(application.getApplicantMemberId()));
    }

    void deleteTeamApplication(String memberId, String teamPostingId, String applicationId) {
        findTeamPosting(teamPostingId).checkAuthor(memberId); // 소유권 검증(§2.6)
        TeamApplication application = teamApplicationRepository.findByIdAndTeamPostingId(applicationId, teamPostingId)
                .orElseThrow(() -> new RecruitException(RecruitErrorCode.APPLICATION_NOT_FOUND, applicationId));
        teamApplicationRepository.delete(application);
    }

    private JobPosting findJobPosting(String jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new RecruitException(RecruitErrorCode.JOB_POSTING_NOT_FOUND, jobPostingId));
    }

    private TeamPosting findTeamPosting(String teamPostingId) {
        return teamPostingRepository.findById(teamPostingId)
                .orElseThrow(() -> new RecruitException(RecruitErrorCode.TEAM_POSTING_NOT_FOUND, teamPostingId));
    }
}
