package com.atcrew.recruit.internal.application;

import com.atcrew.media.MediaAssetInfo;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaService;
import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.JobSeekingPostStatus;
import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.RecruitPostChangedEvent;
import com.atcrew.recruit.RecruitPostType;
import com.atcrew.recruit.internal.persistence.JobApplicationRepository;
import com.atcrew.recruit.internal.persistence.TeamApplicationRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.atcrew.recruit.internal.persistence.JobPostingRepository;
import com.atcrew.recruit.internal.persistence.JobSeekingPostRepository;
import com.atcrew.recruit.internal.persistence.TeamPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 휴지통으로 옮긴 지 보관 기간(기본 1년)이 지난 게시글의 <b>이미지 파일</b>을 정리한다(#200).
 *
 * <p>recruit에는 게시글 영구 삭제 경로가 없어 휴지통에 들어간 게시글의 R2 파일이 무기한 남았다.
 *
 * <p><b>게시글 행과 지원 내역 파기는 기본으로 꺼져 있다</b>({@code recruit.trash.purge-enabled=false}).
 * 지원 내역은 지원자의 개인정보이고 보관 기간은 개인정보처리방침에 적어 동의를 받아야 하는 값이라, 방침이
 * 확정되기 전에는 이미지 파일만 회수한다. 방침이 정해지면 기간을 맞추고 이 설정을 켠다 — 법·업계 관행도
 * "혹시 몰라 계속 보관"이 아니라 "정해진 기간 뒤 파기"다.
 *
 * <p>정리 대상은 media 자산(원본·변형본)과, 자식 행이 없던 시절의 레거시 컬럼(`thumbnail_image`,
 * `reference_images`)이 가리키는 key다. 정리한 뒤에는 그 컬럼을 비운다 — 지운 파일의 key를 남겨두면 배치가
 * 매번 같은 파일을 다시 지우려 한다.
 *
 * <p>게시글마다 별도 트랜잭션으로 처리한다. 한 트랜잭션으로 묶으면 실패 하나가 배치 전체를 롤백시켜 정리가
 * 영영 멈춘다({@code TrashPurgeScheduler}와 같은 이유).
 */
@Component
public class RecruitTrashImageCleanupScheduler {

    static final int BATCH_SIZE = 100;
    /** 보관 기간 하한(일). 설정 실수로 휴지통 게시글의 이미지가 복구 기회 없이 사라지는 것을 기동 시점에 막는다. */
    static final int MIN_RETENTION_DAYS = 30;
    private static final Logger log = LoggerFactory.getLogger(RecruitTrashImageCleanupScheduler.class);

    private final JobPostingRepository jobPostings;
    private final TeamPostingRepository teamPostings;
    private final JobSeekingPostRepository jobSeekingPosts;
    private final MediaService mediaService;
    private final JobApplicationRepository jobApplications;
    private final TeamApplicationRepository teamApplications;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean purgeEnabled;
    private final Period retention;
    private final TransactionTemplate perPosting;

    RecruitTrashImageCleanupScheduler(JobPostingRepository jobPostings, TeamPostingRepository teamPostings,
                                      JobSeekingPostRepository jobSeekingPosts, MediaService mediaService,
                                      JobApplicationRepository jobApplications,
                                      TeamApplicationRepository teamApplications,
                                      ApplicationEventPublisher eventPublisher,
                                      PlatformTransactionManager transactionManager,
                                      @Value("${recruit.trash.retention:P1Y}") Period retention,
                                      @Value("${recruit.trash.purge-enabled:false}") boolean purgeEnabled) {
        if (shortestDays(retention) < MIN_RETENTION_DAYS) {
            throw new IllegalStateException("recruit.trash.retention이 너무 짧다: " + retention
                    + " (어느 달에 적용해도 최소 " + MIN_RETENTION_DAYS + "일이어야 한다).");
        }
        this.jobPostings = jobPostings;
        this.teamPostings = teamPostings;
        this.jobSeekingPosts = jobSeekingPosts;
        this.mediaService = mediaService;
        this.jobApplications = jobApplications;
        this.teamApplications = teamApplications;
        this.eventPublisher = eventPublisher;
        this.purgeEnabled = purgeEnabled;
        this.retention = retention;
        this.perPosting = new TransactionTemplate(transactionManager);
        this.perPosting.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return 이번 실행에서 이미지를 정리한 게시글 수 */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 900_000)
    public int cleanUpExpiredTrashImages() {
        Instant threshold = thresholdAt(Instant.now(), retention);
        int cleaned = 0;
        cleaned += cleanUp(MediaOwnerType.JOB_POSTING, threshold,
                id -> jobPostings.findById(id).map(posting -> {
                    if (posting.getStatus() != JobPostingStatus.DELETED) return null;
                    List<String> legacy = keysOf(posting.getThumbnailImage(), posting.getReferenceImages());
                    posting.clearImagesAfterPurge();
                    return legacy;
                }).orElse(null));
        cleaned += cleanUp(MediaOwnerType.TEAM_POSTING, threshold,
                id -> teamPostings.findById(id).map(posting -> {
                    if (posting.getStatus() != TeamPostingStatus.DELETED) return null;
                    List<String> legacy = keysOf(posting.getThumbnailImage(), posting.getReferenceImages());
                    posting.clearImagesAfterPurge();
                    return legacy;
                }).orElse(null));
        cleaned += cleanUp(MediaOwnerType.JOB_SEEKING_POST, threshold,
                id -> jobSeekingPosts.findById(id).map(post -> {
                    if (post.getStatus() != JobSeekingPostStatus.DELETED) return null;
                    List<String> legacy = keysOf(null, post.getReferenceImages());
                    post.clearImagesAfterPurge();
                    return legacy;
                }).orElse(null));
        if (cleaned > 0) {
            log.info("휴지통 보관 기간 만료 게시글 이미지 정리: count={} retention={} threshold={}",
                    cleaned, retention, threshold);
        }
        return cleaned;
    }

    /**
     * @param legacyKeysOf 게시글을 다시 읽어 레거시 컬럼의 key를 돌려주고 그 컬럼을 비운다. 목록을 뽑은 뒤
     *                     사용자가 복구했으면 {@code null}을 돌려줘 건너뛴다.
     */
    private int cleanUp(MediaOwnerType ownerType, Instant threshold, Function<String, List<String>> legacyKeysOf) {
        int cleaned = 0;
        for (String postingId : expiredIds(ownerType, threshold)) {
            try {
                Boolean done = perPosting.execute(tx -> cleanUpOne(ownerType, postingId, legacyKeysOf));
                if (Boolean.TRUE.equals(done)) {
                    cleaned++;
                }
            } catch (RuntimeException e) {
                log.warn("휴지통 게시글 이미지 정리 실패 — 건너뛰고 다음으로: ownerType={} postingId={}",
                        ownerType, postingId, e);
            }
        }
        return cleaned;
    }

    private boolean cleanUpOne(MediaOwnerType ownerType, String postingId,
                               Function<String, List<String>> legacyKeysOf) {
        List<MediaAssetInfo> assets = mediaService.getAssets(ownerType, postingId);
        List<String> legacyKeys = legacyKeysOf.apply(postingId);
        if (legacyKeys == null) {
            return false;   // 목록을 뽑은 뒤 복구됐거나 사라진 게시글
        }
        List<String> keys = new ArrayList<>(legacyKeys);
        assets.forEach(asset -> {
            addIfPresent(keys, asset.originalKey());
            addIfPresent(keys, asset.thumbKey());
            addIfPresent(keys, asset.thumbAdultKey());
            addIfPresent(keys, asset.originalAvifKey());
        });
        if (keys.isEmpty()) {
            return false;   // 이미 정리된 게시글 — 매 실행마다 같은 일을 반복하지 않는다
        }
        try {
            mediaService.deleteFiles(keys);
        } catch (RuntimeException e) {
            // 지우지 못한 key는 고아 큐로 넘긴다 — 정리 배치가 다시 시도한다(artwork 영구 삭제와 같은 방식).
            log.warn("휴지통 게시글 이미지 R2 삭제 실패 — 고아 큐로 넘긴다: ownerType={} postingId={}",
                    ownerType, postingId, e);
            mediaService.markOrphaned(keys);
        }
        mediaService.deleteAssetsForOwner(ownerType, postingId, Set.copyOf(keys));
        purgeRow(ownerType, postingId);
        return true;
    }

    /**
     * 게시글 행과 지원 내역을 지운다. 지원 내역은 게시글을 삭제 연쇄 없이 참조하므로 먼저 지워야 외래키에
     * 걸리지 않는다. 검색 색인은 변경 이벤트를 받아 사라진 게시글을 걷어낸다.
     */
    private void purgeRow(MediaOwnerType ownerType, String postingId) {
        if (!purgeEnabled) {
            return;
        }
        switch (ownerType) {
            case JOB_POSTING -> {
                jobApplications.deleteByJobPostingId(postingId);
                jobPostings.deleteById(postingId);
                eventPublisher.publishEvent(new RecruitPostChangedEvent(postingId, RecruitPostType.JOB_POSTING));
            }
            case TEAM_POSTING -> {
                teamApplications.deleteByTeamPostingId(postingId);
                teamPostings.deleteById(postingId);
                eventPublisher.publishEvent(new RecruitPostChangedEvent(postingId, RecruitPostType.TEAM_RECRUIT));
            }
            case JOB_SEEKING_POST -> {
                jobSeekingPosts.deleteById(postingId);   // 구직글에는 지원 내역이 없다
                eventPublisher.publishEvent(new RecruitPostChangedEvent(postingId, RecruitPostType.JOB_SEEKING));
            }
            case ARTWORK -> { }
        }
    }

    private List<String> expiredIds(MediaOwnerType ownerType, Instant threshold) {
        PageRequest page = PageRequest.of(0, BATCH_SIZE);
        return switch (ownerType) {
            case JOB_POSTING -> jobPostings.findIdsByStatusAndDeletedAtBefore(JobPostingStatus.DELETED, threshold, page);
            case TEAM_POSTING -> teamPostings.findIdsByStatusAndDeletedAtBefore(TeamPostingStatus.DELETED, threshold, page);
            case JOB_SEEKING_POST -> jobSeekingPosts.findIdsByStatusAndDeletedAtBefore(JobSeekingPostStatus.DELETED, threshold, page);
            case ARTWORK, ARTWORK_THUMBNAIL -> List.of();
        };
    }

    private static List<String> keysOf(String thumbnail, List<String> references) {
        List<String> keys = new ArrayList<>();
        addIfPresent(keys, thumbnail);
        if (references != null) {
            references.forEach(key -> addIfPresent(keys, key));
        }
        return keys;
    }

    private static void addIfPresent(List<String> keys, String key) {
        if (key != null && !key.isBlank() && !keys.contains(key)) {
            keys.add(key);
        }
    }

    /**
     * 보관 기간이 실제로 가장 짧게 적용될 때의 일수 — 1년 365일, 1개월 28일로 센다. 특정 기준일에서 재면
     * "P1M"이 31일로 통과해 놓고 2월에는 28일만 보관하게 된다({@code TrashPurgeScheduler}와 같은 판정이지만,
     * 모듈 경계를 넘지 않으려고 여기에 따로 둔다).
     */
    static long shortestDays(Period retention) {
        if (retention.getYears() < 0 || retention.getMonths() < 0 || retention.getDays() < 0) {
            return -1;
        }
        return retention.getYears() * 365L + retention.getMonths() * 28L + retention.getDays();
    }

    /** now에서 보관 기간을 달력 기준(UTC)으로 뺀 시각. deletedAt이 이보다 이전이면 정리 대상이다. */
    static Instant thresholdAt(Instant now, Period retention) {
        return now.atZone(ZoneOffset.UTC).minus(retention).toInstant();
    }
}
