package com.atcrew.recruit.internal.application;

import com.atcrew.media.MediaAssetInfo;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.MediaService;
import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.internal.domain.JobPosting;
import com.atcrew.recruit.internal.persistence.JobPostingRepository;
import com.atcrew.recruit.internal.persistence.JobSeekingPostRepository;
import com.atcrew.recruit.internal.persistence.TeamPostingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 휴지통 게시글의 이미지 정리(#200). recruit에는 영구 삭제 경로가 없어 버린 게시글의 R2 파일이 무기한 남았다.
 */
class RecruitTrashImageCleanupSchedulerTest {

    private final JobPostingRepository jobPostings = mock(JobPostingRepository.class);
    private final TeamPostingRepository teamPostings = mock(TeamPostingRepository.class);
    private final JobSeekingPostRepository jobSeekingPosts = mock(JobSeekingPostRepository.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    @Test
    void 보관_기간이_지난_게시글의_자산과_레거시_컬럼_키를_함께_지운다() {
        JobPosting posting = trashedPosting("raw/legacy-thumb.png", List.of("raw/legacy-ref.png"));
        givenExpired("posting-1", posting);
        when(mediaService.getAssets(MediaOwnerType.JOB_POSTING, "posting-1")).thenReturn(List.of(
                new MediaAssetInfo("raw/a.png", "thumb/a.avif", null, "original/a.avif",
                        MediaProcessingStatus.DONE, 0, "REFERENCE")));

        int cleaned = scheduler(Period.ofYears(1)).cleanUpExpiredTrashImages();

        assertThat(cleaned).isEqualTo(1);
        verify(mediaService).deleteFiles(List.of(
                "raw/legacy-thumb.png", "raw/legacy-ref.png", "raw/a.png", "thumb/a.avif", "original/a.avif"));
        verify(mediaService).deleteAssetsForOwner(eq(MediaOwnerType.JOB_POSTING), eq("posting-1"), anyCollection());
        // 지운 파일의 key를 게시글에 남겨두면 배치가 매번 같은 파일을 다시 지우려 한다.
        verify(posting).clearImagesAfterPurge();
    }

    @Test
    void 이미_정리된_게시글은_건드리지_않는다() {
        givenExpired("posting-1", trashedPosting(null, List.of()));
        when(mediaService.getAssets(MediaOwnerType.JOB_POSTING, "posting-1")).thenReturn(List.of());

        int cleaned = scheduler(Period.ofYears(1)).cleanUpExpiredTrashImages();

        assertThat(cleaned).isZero();
        verify(mediaService, never()).deleteFiles(any());
    }

    // 목록을 뽑은 뒤 사용자가 복구했을 수 있다.
    @Test
    void 복구된_게시글은_건너뛴다() {
        JobPosting restored = mock(JobPosting.class);
        when(restored.getStatus()).thenReturn(JobPostingStatus.PUBLISHED);
        givenExpired("posting-1", restored);

        int cleaned = scheduler(Period.ofYears(1)).cleanUpExpiredTrashImages();

        assertThat(cleaned).isZero();
        verify(mediaService, never()).deleteFiles(any());
        verify(restored, never()).clearImagesAfterPurge();
    }

    // R2 삭제가 실패해도 추적 기록은 남겨야 한다 — 고아 큐에 넣으면 정리 배치가 다시 시도한다.
    @Test
    void R2_삭제에_실패하면_고아_큐로_넘긴다() {
        givenExpired("posting-1", trashedPosting("raw/legacy-thumb.png", List.of()));
        when(mediaService.getAssets(MediaOwnerType.JOB_POSTING, "posting-1")).thenReturn(List.of());
        doThrow(new IllegalStateException("R2 삭제 실패")).when(mediaService).deleteFiles(any());

        int cleaned = scheduler(Period.ofYears(1)).cleanUpExpiredTrashImages();

        assertThat(cleaned).isEqualTo(1);
        verify(mediaService).markOrphaned(List.of("raw/legacy-thumb.png"));
    }

    @Test
    void 보관_기간이_30일보다_짧으면_기동하지_않는다() {
        assertThatIllegalStateException().isThrownBy(() -> scheduler(Period.ofDays(29)));
        assertThatIllegalStateException().isThrownBy(() -> scheduler(Period.ofMonths(1).minusDays(3)));
    }

    // 보관 기간은 달력 기준이라 윤년을 끼어도 정확히 1년이다(artwork 휴지통과 같은 판정).
    @Test
    void 보관_기간은_윤년을_끼어도_달력_기준_1년이다() {
        Instant threshold = RecruitTrashImageCleanupScheduler.thresholdAt(
                Instant.parse("2028-02-29T13:00:00Z"), Period.ofYears(1));

        assertThat(threshold).isEqualTo(Instant.parse("2027-02-28T13:00:00Z"));
    }

    private RecruitTrashImageCleanupScheduler scheduler(Period retention) {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new RecruitTrashImageCleanupScheduler(jobPostings, teamPostings, jobSeekingPosts,
                mediaService, txManager, retention);
    }

    private void givenExpired(String postingId, JobPosting posting) {
        when(jobPostings.findIdsByStatusAndDeletedAtBefore(eq(JobPostingStatus.DELETED), any(), any()))
                .thenReturn(List.of(postingId));
        when(jobPostings.findById(postingId)).thenReturn(Optional.of(posting));
        when(teamPostings.findIdsByStatusAndDeletedAtBefore(any(), any(), any())).thenReturn(List.of());
        when(jobSeekingPosts.findIdsByStatusAndDeletedAtBefore(any(), any(), any())).thenReturn(List.of());
    }

    /** 레거시 컬럼(자식 행이 없던 시절 데이터)에 key가 남아 있는 휴지통 게시글. */
    private JobPosting trashedPosting(String thumbnail, List<String> references) {
        JobPosting posting = mock(JobPosting.class);
        when(posting.getStatus()).thenReturn(JobPostingStatus.DELETED);
        when(posting.getThumbnailImage()).thenReturn(thumbnail);
        when(posting.getReferenceImages()).thenReturn(references);
        return posting;
    }
}
