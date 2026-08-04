package com.atcrew.recruit.internal.application;

import com.atcrew.media.MediaAssetProcessedEvent;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.recruit.internal.domain.JobPosting;
import com.atcrew.recruit.internal.domain.JobSeekingPost;
import com.atcrew.recruit.internal.domain.RecruitPostingImage;
import com.atcrew.recruit.internal.domain.TeamPosting;
import com.atcrew.recruit.internal.persistence.JobPostingRepository;
import com.atcrew.recruit.internal.persistence.JobSeekingPostRepository;
import com.atcrew.recruit.internal.persistence.TeamPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * media 모듈이 발행하는 {@link MediaAssetProcessedEvent}를 수신해 게시글 이미지 자식 행과
 * 게시글의 이미지 처리 상태를 갱신한다 (docs/design/media-module-design.md §5, §10.3).
 *
 * <p>{@code ownerType}으로 세 자식 테이블 중 하나를 고른다. {@code ARTWORK}는 artwork 모듈이 자기
 * 리스너에서 처리하므로 여기서는 무시한다.
 *
 * <p>{@code @ApplicationModuleListener}는 원본 트랜잭션(media webhook 처리) 커밋 이후 비동기로
 * 실행된다 — {@code ArtistProfileViewListener}와 같은 이유다.
 */
@Component
class RecruitMediaEventListener {

    private static final Logger log = LoggerFactory.getLogger(RecruitMediaEventListener.class);

    private final RecruitImageService recruitImageService;
    private final JobPostingRepository jobPostingRepository;
    private final TeamPostingRepository teamPostingRepository;
    private final JobSeekingPostRepository jobSeekingPostRepository;

    RecruitMediaEventListener(RecruitImageService recruitImageService, JobPostingRepository jobPostingRepository,
            TeamPostingRepository teamPostingRepository, JobSeekingPostRepository jobSeekingPostRepository) {
        this.recruitImageService = recruitImageService;
        this.jobPostingRepository = jobPostingRepository;
        this.teamPostingRepository = teamPostingRepository;
        this.jobSeekingPostRepository = jobSeekingPostRepository;
    }

    @ApplicationModuleListener
    void onMediaAssetProcessed(MediaAssetProcessedEvent event) {
        if (event.ownerType() == MediaOwnerType.ARTWORK) {
            return;
        }
        List<? extends RecruitPostingImage> images =
                recruitImageService.findImages(event.ownerType(), event.ownerId());
        images.stream()
                .filter(image -> event.imageKey().equals(image.getOriginalKey()))
                .findFirst()
                .ifPresentOrElse(
                        image -> image.markProcessed(event.thumbKey(), event.originalAvifKey(), event.status()),
                        () -> log.warn("처리 결과에 해당하는 게시글 이미지가 없습니다: ownerType={} ownerId={} imageKey={}",
                                event.ownerType(), event.ownerId(), event.imageKey()));

        // 부분 실패 허용 — PENDING이 하나도 없고 DONE이 하나 이상이면 READY (설계 §5·§10.3).
        if (RecruitPostingImage.readyFor(images)) {
            markReady(event.ownerType(), event.ownerId());
        }
    }

    private void markReady(MediaOwnerType ownerType, String postingId) {
        switch (ownerType) {
            case JOB_POSTING -> jobPostingRepository.findById(postingId)
                    .ifPresent(JobPosting::markImageProcessingReady);
            case TEAM_POSTING -> teamPostingRepository.findById(postingId)
                    .ifPresent(TeamPosting::markImageProcessingReady);
            case JOB_SEEKING_POST -> jobSeekingPostRepository.findById(postingId)
                    .ifPresent(JobSeekingPost::markImageProcessingReady);
            case ARTWORK -> throw new IllegalStateException("도달 불가 — ARTWORK는 진입 시점에 걸러진다");
        }
    }
}
