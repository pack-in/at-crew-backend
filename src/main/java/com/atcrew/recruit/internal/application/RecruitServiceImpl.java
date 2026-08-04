package com.atcrew.recruit.internal.application;

import com.atcrew.common.response.CursorPage;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.recruit.ApplicationInfo;
import com.atcrew.recruit.ApplicationReviewStatus;
import com.atcrew.recruit.CommunityJobPostingCardInfo;
import com.atcrew.recruit.CommunityTeamRecruitCardInfo;
import com.atcrew.recruit.CreateApplicationCommand;
import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.CreateJobSeekingPostCommand;
import com.atcrew.recruit.CreateTeamPostingCommand;
import com.atcrew.recruit.JobPostingInfo;
import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.JobSeekingPostInfo;
import com.atcrew.recruit.LikedArtistInfo;
import com.atcrew.recruit.RecentlyViewedArtistInfo;
import com.atcrew.recruit.RecruitSearchPage;
import com.atcrew.recruit.RecruitSearchQuery;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@Transactional
class RecruitServiceImpl implements RecruitService {

    private final JobPostingRepository jobPostingRepository;
    private final TeamPostingRepository teamPostingRepository;
    private final JobSeekingPostService jobSeekingPostService;
    private final ApplicationService applicationService;
    private final LikedArtistService likedArtistService;
    private final RecruitSearchService recruitSearchService;
    private final AuthorNameResolver authorNameResolver;
    private final RecruitImageService recruitImageService;

    RecruitServiceImpl(JobPostingRepository jobPostingRepository, TeamPostingRepository teamPostingRepository,
            JobSeekingPostService jobSeekingPostService, ApplicationService applicationService,
            LikedArtistService likedArtistService, RecruitSearchService recruitSearchService,
            AuthorNameResolver authorNameResolver, RecruitImageService recruitImageService) {
        this.jobPostingRepository = jobPostingRepository;
        this.teamPostingRepository = teamPostingRepository;
        this.jobSeekingPostService = jobSeekingPostService;
        this.applicationService = applicationService;
        this.likedArtistService = likedArtistService;
        this.recruitSearchService = recruitSearchService;
        this.authorNameResolver = authorNameResolver;
        this.recruitImageService = recruitImageService;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<CommunityJobPostingCardInfo> getJobPostingFeed(String cursor, int size) {
        Instant now = Instant.now();
        List<JobPosting> postings = findPublished(cursor, size, now);
        return toCardPage(postings, size, now);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<CommunityTeamRecruitCardInfo> getTeamRecruitFeed(String cursor, int size) {
        Instant now = Instant.now();
        List<TeamPosting> postings = findPublishedTeamPostings(cursor, size, now);
        return toTeamCardPage(postings, size, now);
    }

    // === 타 모듈 연동 (§6) ===

    @Override
    @Transactional(readOnly = true)
    public boolean hasOpenJobPosting(String companyMemberId) {
        return jobPostingRepository.existsByAuthorMemberIdAndStatus(companyMemberId, JobPostingStatus.PUBLISHED);
    }

    @Override
    @Transactional(readOnly = true)
    public RecruitSearchPage searchPosts(RecruitSearchQuery query) {
        return recruitSearchService.search(query);
    }

    @Override
    public JobPostingInfo createJobPosting(String memberId, CreateJobPostingCommand command) {
        JobPosting jobPosting = JobPosting.create(memberId, command);
        if (command.submit()) {
            jobPosting.submitForApproval();
        }
        JobPosting saved = jobPostingRepository.save(jobPosting);
        // 이미지는 presign으로 발급받은 key로 들어온다 — media 모듈에 등록해 Worker 변환을 트리거한다(설계 §10.3).
        RecruitImageService.apply(
                recruitImageService.register(MediaOwnerType.JOB_POSTING, saved.getId(),
                        command.thumbnailImage(), command.referenceImages()),
                saved::markImageProcessingPending, saved::markImageProcessingReady);
        return toInfo(saved);
    }

    @Override
    public JobPostingInfo updateJobPosting(String memberId, String jobPostingId, UpdateJobPostingCommand command) {
        JobPosting jobPosting = getOwned(jobPostingId, memberId);
        if (jobPosting.getStatus() == JobPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.JOB_POSTING_NOT_FOUND, jobPostingId);
        }
        jobPosting.updateContent(command);
        // 부분 업데이트라 이미지 필드를 실제로 보낸 요청만 media 재등록 대상이다(설계 §10.3).
        if (command.thumbnailImage() != null || command.referenceImages() != null) {
            RecruitImageService.apply(
                    recruitImageService.replace(MediaOwnerType.JOB_POSTING, jobPostingId,
                            jobPosting.getThumbnailImage(), jobPosting.getReferenceImages()),
                    jobPosting::markImageProcessingPending, jobPosting::markImageProcessingReady);
        }
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
        Instant now = Instant.now();
        List<JobPosting> postings = findPublished(cursor, size, now);
        return toInfoPage(postings, size, now);
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

    @Override
    public JobPostingInfo boostJobPosting(String memberId, String jobPostingId) {
        JobPosting jobPosting = getOwned(jobPostingId, memberId);
        // TODO(결제): Polar 결제 모듈(로드맵 5번) 완성 후 "구매한 끌어올리기 개수" 확인·차감을 여기에 연결한다(설계 §2.1.1, §7).
        jobPosting.boost(Instant.now());
        return toInfo(jobPosting);
    }

    // === TeamPosting CRUD (§4.2) ===

    @Override
    public TeamPostingInfo createTeamPosting(String memberId, CreateTeamPostingCommand command) {
        TeamPosting teamPosting = TeamPosting.create(memberId, command);
        TeamPosting saved = teamPostingRepository.save(teamPosting);
        // 이미지는 presign으로 발급받은 key로 들어온다 — media 모듈에 등록해 Worker 변환을 트리거한다(설계 §10.3).
        RecruitImageService.apply(
                recruitImageService.register(MediaOwnerType.TEAM_POSTING, saved.getId(),
                        command.thumbnailImage(), command.referenceImages()),
                saved::markImageProcessingPending, saved::markImageProcessingReady);
        return toTeamInfo(saved);
    }

    @Override
    public TeamPostingInfo updateTeamPosting(String memberId, String teamPostingId, UpdateTeamPostingCommand command) {
        TeamPosting teamPosting = getOwnedTeamPosting(teamPostingId, memberId);
        if (teamPosting.getStatus() == TeamPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.TEAM_POSTING_NOT_FOUND, teamPostingId);
        }
        teamPosting.updateContent(command);
        // 부분 업데이트라 이미지 필드를 실제로 보낸 요청만 media 재등록 대상이다(설계 §10.3).
        if (command.thumbnailImage() != null || command.referenceImages() != null) {
            RecruitImageService.apply(
                    recruitImageService.replace(MediaOwnerType.TEAM_POSTING, teamPostingId,
                            teamPosting.getThumbnailImage(), teamPosting.getReferenceImages()),
                    teamPosting::markImageProcessingPending, teamPosting::markImageProcessingReady);
        }
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
        Instant now = Instant.now();
        List<TeamPosting> postings = findPublishedTeamPostings(cursor, size, now);
        return toTeamInfoPage(postings, size, now);
    }

    @Override
    public TeamPostingInfo boostTeamPosting(String memberId, String teamPostingId) {
        TeamPosting teamPosting = getOwnedTeamPosting(teamPostingId, memberId);
        // TODO(결제): Polar 결제 모듈(로드맵 5번) 완성 후 "구매한 끌어올리기 개수" 확인·차감을 여기에 연결한다(설계 §2.1.1, §7).
        teamPosting.boost(Instant.now());
        return toTeamInfo(teamPosting);
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

    // === 지원/지원자 관리 (§2.4, §2.5, §2.6) — 내부 협력자 ApplicationService에 위임 ===

    @Override
    public ApplicationInfo applyToJobPosting(String memberId, String jobPostingId, CreateApplicationCommand command) {
        return applicationService.applyToJobPosting(memberId, jobPostingId, command);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ApplicationInfo> getJobPostingApplications(String memberId, String jobPostingId, String cursor,
            int size) {
        return applicationService.getJobPostingApplications(memberId, jobPostingId, cursor, size);
    }

    @Override
    public ApplicationInfo updateJobApplicationReviewStatus(String memberId, String jobPostingId, String applicationId,
            ApplicationReviewStatus reviewStatus) {
        return applicationService.updateJobApplicationReviewStatus(memberId, jobPostingId, applicationId, reviewStatus);
    }

    @Override
    public void deleteJobApplication(String memberId, String jobPostingId, String applicationId) {
        applicationService.deleteJobApplication(memberId, jobPostingId, applicationId);
    }

    @Override
    public ApplicationInfo applyToTeamPosting(String memberId, String teamPostingId, CreateApplicationCommand command) {
        return applicationService.applyToTeamPosting(memberId, teamPostingId, command);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ApplicationInfo> getTeamPostingApplications(String memberId, String teamPostingId, String cursor,
            int size) {
        return applicationService.getTeamPostingApplications(memberId, teamPostingId, cursor, size);
    }

    @Override
    public ApplicationInfo updateTeamApplicationReviewStatus(String memberId, String teamPostingId,
            String applicationId, ApplicationReviewStatus reviewStatus) {
        return applicationService.updateTeamApplicationReviewStatus(memberId, teamPostingId, applicationId, reviewStatus);
    }

    @Override
    public void deleteTeamApplication(String memberId, String teamPostingId, String applicationId) {
        applicationService.deleteTeamApplication(memberId, teamPostingId, applicationId);
    }

    // === 관심 작가 (§2.7, §4.3) — 내부 협력자 LikedArtistService에 위임 ===

    @Override
    public void likeArtist(String companyMemberId, String artistMemberId) {
        likedArtistService.likeArtist(companyMemberId, artistMemberId);
    }

    @Override
    public void unlikeArtist(String companyMemberId, String artistMemberId) {
        likedArtistService.unlikeArtist(companyMemberId, artistMemberId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<LikedArtistInfo> getLikedArtists(String companyMemberId, String q, String cursor, int size) {
        return likedArtistService.getLikedArtists(companyMemberId, q, cursor, size);
    }

    @Override
    public void recordArtistView(String companyMemberId, String artistMemberId) {
        likedArtistService.recordArtistView(companyMemberId, artistMemberId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<RecentlyViewedArtistInfo> getRecentlyViewedArtists(String companyMemberId, String cursor,
            int size) {
        return likedArtistService.getRecentlyViewedArtists(companyMemberId, cursor, size);
    }

    // 공개 목록은 끌어올리기 적용 글을 상단 고정하므로 (정렬 키, id) 복합 커서를 쓴다(설계 §2.1.1).
    private List<JobPosting> findPublished(String cursor, int size, Instant now) {
        Pageable pageable = PageRequest.of(0, size + 1);
        if (cursor == null) {
            return jobPostingRepository.findPublishedFirstPage(
                    JobPostingStatus.PUBLISHED, now, Instant.EPOCH, pageable);
        }
        CompositeCursor decoded = CompositeCursor.decode(cursor);
        return jobPostingRepository.findPublishedNextPage(
                JobPostingStatus.PUBLISHED, now, Instant.EPOCH, decoded.sortAt(), decoded.id(), pageable);
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

    // 내 목록/휴지통/관리자 목록 — id 단일 커서
    private CursorPage<JobPostingInfo> toInfoPage(List<JobPosting> postings, int size) {
        return toInfoPage(postings, size, JobPosting::getId);
    }

    // 공개 목록 — 끌어올리기 상단고정 정렬에 맞춘 (정렬 키, id) 복합 커서
    private CursorPage<JobPostingInfo> toInfoPage(List<JobPosting> postings, int size, Instant now) {
        return toInfoPage(postings, size, p -> CompositeCursor.encodeBoost(p.getBoostedUntil(), p.getId(), now));
    }

    private CursorPage<JobPostingInfo> toInfoPage(List<JobPosting> postings, int size,
            Function<JobPosting, String> cursorExtractor) {
        if (postings.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = postings.size() > size;
        List<JobPosting> page = hasNext ? postings.subList(0, size) : postings;
        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(JobPosting::getAuthorMemberId).toList());
        Map<String, PostingImages> images = recruitImageService.loadAll(
                MediaOwnerType.JOB_POSTING, page.stream().map(JobPosting::getId).toList());
        List<JobPostingInfo> items = page.stream()
                .map(p -> JobPostingMapper.toInfo(p, authorNames.get(p.getAuthorMemberId()), images.get(p.getId())))
                .toList();
        String nextCursor = hasNext ? cursorExtractor.apply(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    private CursorPage<CommunityJobPostingCardInfo> toCardPage(List<JobPosting> postings, int size, Instant now) {
        if (postings.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = postings.size() > size;
        List<JobPosting> page = hasNext ? postings.subList(0, size) : postings;
        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(JobPosting::getAuthorMemberId).toList());
        Map<String, PostingImages> images = recruitImageService.loadAll(
                MediaOwnerType.JOB_POSTING, page.stream().map(JobPosting::getId).toList());
        List<CommunityJobPostingCardInfo> items = page.stream()
                .map(p -> JobPostingMapper.toCardInfo(p, authorNames.get(p.getAuthorMemberId()), images.get(p.getId())))
                .toList();
        JobPosting last = page.get(page.size() - 1);
        String nextCursor = hasNext ? CompositeCursor.encodeBoost(last.getBoostedUntil(), last.getId(), now) : null;
        return CursorPage.of(items, nextCursor);
    }

    private JobPostingInfo toInfo(JobPosting jobPosting) {
        return JobPostingMapper.toInfo(jobPosting, authorNameResolver.resolve(jobPosting.getAuthorMemberId()),
                recruitImageService.load(MediaOwnerType.JOB_POSTING, jobPosting.getId()));
    }

    // 공개 목록은 끌어올리기 적용 글을 상단 고정하므로 (정렬 키, id) 복합 커서를 쓴다(설계 §2.1.1).
    private List<TeamPosting> findPublishedTeamPostings(String cursor, int size, Instant now) {
        Pageable pageable = PageRequest.of(0, size + 1);
        if (cursor == null) {
            return teamPostingRepository.findPublishedFirstPage(
                    TeamPostingStatus.PUBLISHED, now, Instant.EPOCH, pageable);
        }
        CompositeCursor decoded = CompositeCursor.decode(cursor);
        return teamPostingRepository.findPublishedNextPage(
                TeamPostingStatus.PUBLISHED, now, Instant.EPOCH, decoded.sortAt(), decoded.id(), pageable);
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

    // 내 목록/휴지통 — id 단일 커서
    private CursorPage<TeamPostingInfo> toTeamInfoPage(List<TeamPosting> postings, int size) {
        return toTeamInfoPage(postings, size, TeamPosting::getId);
    }

    // 공개 목록 — 끌어올리기 상단고정 정렬에 맞춘 (정렬 키, id) 복합 커서
    private CursorPage<TeamPostingInfo> toTeamInfoPage(List<TeamPosting> postings, int size, Instant now) {
        return toTeamInfoPage(postings, size, p -> CompositeCursor.encodeBoost(p.getBoostedUntil(), p.getId(), now));
    }

    private CursorPage<TeamPostingInfo> toTeamInfoPage(List<TeamPosting> postings, int size,
            Function<TeamPosting, String> cursorExtractor) {
        if (postings.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = postings.size() > size;
        List<TeamPosting> page = hasNext ? postings.subList(0, size) : postings;
        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(TeamPosting::getAuthorMemberId).toList());
        Map<String, PostingImages> images = recruitImageService.loadAll(
                MediaOwnerType.TEAM_POSTING, page.stream().map(TeamPosting::getId).toList());
        List<TeamPostingInfo> items = page.stream()
                .map(p -> TeamPostingMapper.toInfo(p, authorNames.get(p.getAuthorMemberId()), images.get(p.getId())))
                .toList();
        String nextCursor = hasNext ? cursorExtractor.apply(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    private CursorPage<CommunityTeamRecruitCardInfo> toTeamCardPage(List<TeamPosting> postings, int size, Instant now) {
        if (postings.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = postings.size() > size;
        List<TeamPosting> page = hasNext ? postings.subList(0, size) : postings;
        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(TeamPosting::getAuthorMemberId).toList());
        Map<String, PostingImages> images = recruitImageService.loadAll(
                MediaOwnerType.TEAM_POSTING, page.stream().map(TeamPosting::getId).toList());
        List<CommunityTeamRecruitCardInfo> items = page.stream()
                .map(p -> TeamPostingMapper.toCardInfo(p, authorNames.get(p.getAuthorMemberId()), images.get(p.getId())))
                .toList();
        TeamPosting last = page.get(page.size() - 1);
        String nextCursor = hasNext ? CompositeCursor.encodeBoost(last.getBoostedUntil(), last.getId(), now) : null;
        return CursorPage.of(items, nextCursor);
    }

    private TeamPostingInfo toTeamInfo(TeamPosting teamPosting) {
        return TeamPostingMapper.toInfo(teamPosting, authorNameResolver.resolve(teamPosting.getAuthorMemberId()),
                recruitImageService.load(MediaOwnerType.TEAM_POSTING, teamPosting.getId()));
    }
}
