package com.atcrew.recruit.internal.application;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.recruit.internal.domain.JobPostingImage;
import com.atcrew.recruit.internal.domain.JobSeekingPostImage;
import com.atcrew.recruit.internal.domain.RecruitImageRole;
import com.atcrew.recruit.internal.domain.RecruitPostingImage;
import com.atcrew.recruit.internal.domain.TeamPostingImage;
import com.atcrew.recruit.internal.persistence.JobPostingImageRepository;
import com.atcrew.recruit.internal.persistence.JobSeekingPostImageRepository;
import com.atcrew.recruit.internal.persistence.TeamPostingImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 게시글 이미지 자식 테이블 관리 + media 모듈 위임 (docs/design/media-module-design.md §10).
 *
 * <p>recruit은 성인 게이팅 대상이 아니라 항상 {@link MediaVariantProfile#STANDARD}로 요청한다(§3).
 * 게시글 본문의 {@code thumbnailImage}/{@code referenceImages} 컬럼은 그대로 유지하고(§10.4),
 * 이 클래스가 관리하는 자식 행이 변환본 키를 담아 응답 조립에 쓰인다.
 */
@Service
@Transactional
class RecruitImageService {

    /** 이미지 동기화 결과 — 호출자가 게시글의 imageProcessingStatus를 어떻게 둘지 결정하는 데 쓴다. */
    enum ImageSyncResult {
        /** 요청이 이미지 필드를 건드리지 않았다 — 상태를 그대로 둔다. */
        UNCHANGED,
        /** 새 이미지를 등록해 Worker 처리를 기다린다 — PENDING. */
        PROCESSING,
        /** 처리할 이미지가 없다 — READY. */
        NO_IMAGES
    }

    /** 동기화 결과를 게시글의 이미지 처리 상태에 반영한다. */
    static void apply(ImageSyncResult result, Runnable markPending, Runnable markReady) {
        switch (result) {
            case PROCESSING -> markPending.run();
            case NO_IMAGES -> markReady.run();
            case UNCHANGED -> { }
        }
    }

    private final MediaService mediaService;
    private final JobPostingImageRepository jobPostingImages;
    private final TeamPostingImageRepository teamPostingImages;
    private final JobSeekingPostImageRepository jobSeekingPostImages;

    RecruitImageService(MediaService mediaService, JobPostingImageRepository jobPostingImages,
            TeamPostingImageRepository teamPostingImages, JobSeekingPostImageRepository jobSeekingPostImages) {
        this.mediaService = mediaService;
        this.jobPostingImages = jobPostingImages;
        this.teamPostingImages = teamPostingImages;
        this.jobSeekingPostImages = jobSeekingPostImages;
    }

    // === 쓰기 ===

    /**
     * 게시글 생성 시 — 자식 행을 PENDING으로 적재하고 media 모듈에 등록·Worker 트리거를 위임한다(§10.3).
     * 새 게시글이라 기존 자산이 없으므로 {@code registerAndTriggerProcessing}을 쓴다.
     */
    ImageSyncResult register(MediaOwnerType ownerType, String postingId, String thumbnail,
            List<String> references) {
        List<ImageSlot> slots = slots(thumbnail, references);
        if (slots.isEmpty()) {
            return ImageSyncResult.NO_IMAGES;
        }
        saveSlots(ownerType, postingId, slots);
        mediaService.registerAndTriggerProcessing(ownerType, postingId, keysOf(slots), MediaVariantProfile.STANDARD);
        return ImageSyncResult.PROCESSING;
    }

    /**
     * 게시글 수정 시 — 이미지 목록이 실제로 바뀐 경우에만 기존 자산을 고아 처리하고 새로 등록한다.
     *
     * <p>{@code registerAndTriggerProcessing}이 아니라 {@code replaceAndTriggerProcessing}을 쓴다(§4):
     * media_assets는 (ownerType, ownerId, ordinal) 유니크 제약이 있어 재등록이 충돌하고, 교체된 옛 키는
     * 고아 정리 큐에 넣어야 R2에 파일이 남지 않는다.
     */
    ImageSyncResult replace(MediaOwnerType ownerType, String postingId, String thumbnail,
            List<String> references) {
        List<ImageSlot> slots = slots(thumbnail, references);
        List<String> newKeys = keysOf(slots);
        List<? extends RecruitPostingImage> existing = findImages(ownerType, postingId);
        if (newKeys.equals(existing.stream().map(RecruitPostingImage::getOriginalKey).toList())) {
            // 같은 키를 다시 보낸 경우 — 이미 끝난 변환을 버리고 재처리하지 않는다.
            return ImageSyncResult.UNCHANGED;
        }
        if (newKeys.isEmpty()) {
            // 이미지를 모두 지운 경우. media는 새 키가 없으면 교체 API를 받지 않으므로 파일 고아 처리와
            // media_assets 행 삭제를 직접 호출한다(등록될 때까지 미루지 않음).
            mediaService.markOrphaned(derivedKeysOf(existing));
            mediaService.deleteAssetsForOwner(ownerType, postingId);
            deleteImages(ownerType, postingId);
            return ImageSyncResult.NO_IMAGES;
        }
        deleteImages(ownerType, postingId);
        saveSlots(ownerType, postingId, slots);
        mediaService.replaceAndTriggerProcessing(ownerType, postingId, newKeys, MediaVariantProfile.STANDARD);
        return ImageSyncResult.PROCESSING;
    }

    // === 읽기 ===

    /** 자식 행이 없으면(이 변경 이전 데이터) {@code null} — 호출자가 기존 컬럼으로 폴백한다. */
    @Transactional(readOnly = true)
    PostingImages load(MediaOwnerType ownerType, String postingId) {
        List<? extends RecruitPostingImage> images = findImages(ownerType, postingId);
        return images.isEmpty() ? null : PostingImages.of(images);
    }

    /** 목록 조회용 일괄 로딩 — 자식 행이 없는 게시글 ID는 결과 맵에 담기지 않는다. */
    @Transactional(readOnly = true)
    Map<String, PostingImages> loadAll(MediaOwnerType ownerType, Collection<String> postingIds) {
        if (postingIds.isEmpty()) {
            return Map.of();
        }
        return findImages(ownerType, postingIds).stream()
                .collect(Collectors.groupingBy(RecruitPostingImage::getPostingId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> PostingImages.of(e.getValue())));
    }

    // === 타입별 저장소 분기 ===

    List<? extends RecruitPostingImage> findImages(MediaOwnerType ownerType, String postingId) {
        return switch (ownerType) {
            case JOB_POSTING -> jobPostingImages.findByPostingIdOrderByOrdinalAsc(postingId);
            case TEAM_POSTING -> teamPostingImages.findByPostingIdOrderByOrdinalAsc(postingId);
            case JOB_SEEKING_POST -> jobSeekingPostImages.findByPostingIdOrderByOrdinalAsc(postingId);
            case ARTWORK -> throw new IllegalArgumentException("recruit이 다루는 ownerType이 아닙니다: " + ownerType);
        };
    }

    private List<? extends RecruitPostingImage> findImages(MediaOwnerType ownerType, Collection<String> postingIds) {
        return switch (ownerType) {
            case JOB_POSTING -> jobPostingImages.findByPostingIdInOrderByOrdinalAsc(postingIds);
            case TEAM_POSTING -> teamPostingImages.findByPostingIdInOrderByOrdinalAsc(postingIds);
            case JOB_SEEKING_POST -> jobSeekingPostImages.findByPostingIdInOrderByOrdinalAsc(postingIds);
            case ARTWORK -> throw new IllegalArgumentException("recruit이 다루는 ownerType이 아닙니다: " + ownerType);
        };
    }

    private void deleteImages(MediaOwnerType ownerType, String postingId) {
        switch (ownerType) {
            case JOB_POSTING -> jobPostingImages.deleteByPostingId(postingId);
            case TEAM_POSTING -> teamPostingImages.deleteByPostingId(postingId);
            case JOB_SEEKING_POST -> jobSeekingPostImages.deleteByPostingId(postingId);
            case ARTWORK -> throw new IllegalArgumentException("recruit이 다루는 ownerType이 아닙니다: " + ownerType);
        }
    }

    private void saveSlots(MediaOwnerType ownerType, String postingId, List<ImageSlot> slots) {
        switch (ownerType) {
            case JOB_POSTING -> slots.forEach(s ->
                    jobPostingImages.save(JobPostingImage.pending(postingId, s.role(), s.ordinal(), s.key())));
            case TEAM_POSTING -> slots.forEach(s ->
                    teamPostingImages.save(TeamPostingImage.pending(postingId, s.role(), s.ordinal(), s.key())));
            case JOB_SEEKING_POST -> slots.forEach(s ->
                    jobSeekingPostImages.save(JobSeekingPostImage.pending(postingId, s.role(), s.ordinal(), s.key())));
            case ARTWORK -> throw new IllegalArgumentException("recruit이 다루는 ownerType이 아닙니다: " + ownerType);
        }
    }

    // === 헬퍼 ===

    private record ImageSlot(RecruitImageRole role, int ordinal, String key) {
    }

    /** THUMBNAIL=0, REFERENCE=1..n — media_assets에 넘기는 키 리스트의 인덱스와 같은 축을 쓴다. */
    private static List<ImageSlot> slots(String thumbnail, List<String> references) {
        List<ImageSlot> slots = new ArrayList<>();
        if (isPresent(thumbnail)) {
            slots.add(new ImageSlot(RecruitImageRole.THUMBNAIL, slots.size(), thumbnail));
        }
        if (references != null) {
            for (String key : references) {
                if (isPresent(key)) {
                    slots.add(new ImageSlot(RecruitImageRole.REFERENCE, slots.size(), key));
                }
            }
        }
        return slots;
    }

    private static List<String> keysOf(List<ImageSlot> slots) {
        return slots.stream().map(ImageSlot::key).toList();
    }

    // 교체·삭제 시 R2에서 지워야 할 키 — 업로드 원본과 Worker가 만든 변환본 전부.
    private static List<String> derivedKeysOf(List<? extends RecruitPostingImage> images) {
        return images.stream()
                .flatMap(i -> java.util.stream.Stream.of(i.getOriginalKey(), i.getThumbKey(), i.getOriginalAvifKey()))
                .filter(RecruitImageService::isPresent)
                .toList();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
