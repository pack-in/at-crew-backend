package com.atcrew.recruit.internal.application;

import com.atcrew.media.MediaAssetInfo;
import com.atcrew.media.MediaAssetSpec;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import com.atcrew.recruit.internal.domain.RecruitImageRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 게시글 이미지를 media 모듈에 위임한다 (docs/design/media-module-design.md §10).
 *
 * <p>이미지의 상태와 변형본 key는 {@code media_assets} 한 곳에만 있다(#193). 예전에는 게시글마다 자식
 * 테이블을 두고 같은 값을 이중으로 갖다가, 교체할 때 두 축이 어긋나 남긴 이미지가 깨졌다.
 *
 * <p>recruit은 항상 {@link MediaVariantProfile#ORIGINAL}로 요청한다 — 썸네일 슬롯도 카드·OG 모두 원본 변환본을
 * 그대로 쓰고(PostingImages), 성인 게이팅 대상도 아니라 thumb·블러본이 필요 없다(§3).
 * 썸네일과 참고 이미지는 media의 슬롯 이름({@link RecruitImageRole})으로 가른다 — 썸네일이 없으면 참고
 * 이미지가 0번을 차지하므로 ordinal만으로는 구분할 수 없다.
 */
@Service
@Transactional
class RecruitImageService {

    /** 이미지 동기화 결과 — 호출자가 게시글의 imageProcessingStatus를 어떻게 둘지 결정하는 데 쓴다. */
    enum ImageSyncResult {
        /** 처리를 기다리는 이미지가 있다 — PENDING. */
        PROCESSING,
        /** 기다릴 이미지가 없다(이미지가 없거나 모두 끝났다) — READY. */
        READY
    }

    /** 동기화 결과를 게시글의 이미지 처리 상태에 반영한다. */
    static void apply(ImageSyncResult result, Runnable markPending, Runnable markReady) {
        switch (result) {
            case PROCESSING -> markPending.run();
            case READY -> markReady.run();
        }
    }

    private final MediaService mediaService;

    RecruitImageService(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    // === 쓰기 ===

    /**
     * 게시글의 이미지 목록을 요청과 같게 맞춘다. 남는 이미지는 media가 변환 결과를 그대로 두고, 빠진 것만
     * 고아 큐로 보낸다(#193) — 예전에는 목록이 조금이라도 달라지면 전량 교체라 남긴 이미지가 깨졌다.
     */
    /**
     * @param previouslyStored 수정 전에 이 게시글에 저장돼 있던 key. media 자산이 없는 옛 게시글(레거시 컬럼만
     *                         있는 데이터)도 그 key를 다시 보낼 수 있어야 하므로 호출자가 넘긴다 — 넘기지 않으면
     *                         옛 게시글은 소유 검증에 걸려 영영 수정할 수 없다.
     */
    ImageSyncResult sync(String memberId, MediaOwnerType ownerType, String postingId, String thumbnail,
            List<String> references, Collection<String> previouslyStored) {
        assertRecruitOwner(ownerType);
        assertKeysOwned(memberId, ownerType, postingId, thumbnail, references, previouslyStored);
        List<MediaAssetInfo> assets = mediaService.syncAssets(ownerType, postingId,
                specs(thumbnail, references), MediaVariantProfile.ORIGINAL, MediaQualityTier.WEB);
        return resultOf(assets);
    }

    /**
     * 클라이언트가 보낸 R2 key가 본인이 발급받은 것인지 확인한다(#190). key는 공개 응답에 그대로 실리므로,
     * 검증하지 않으면 남의 key를 게시글에 붙여 그 파일의 삭제를 막거나 남의 이미지를 자기 게시글에 띄울 수 있다.
     *
     * <p>이미 이 게시글에 등록된 key는 대상에서 뺀다 — 프론트가 수정마다 기존 값을 다시 보내므로, 서명이 없던
     * 시절의 key도 계속 수정할 수 있어야 한다.
     */
    private void assertKeysOwned(String memberId, MediaOwnerType ownerType, String postingId, String thumbnail,
            List<String> references, Collection<String> previouslyStored) {
        List<String> keys = specs(thumbnail, references).stream().map(MediaAssetSpec::key).toList();
        // 같은 key를 두 번 보내면 콜백이 행을 특정하지 못해 처리가 멈춘다. media가 거부하지만 그 예외는 500이 되므로
        // 여기서 400으로 돌려준다.
        if (keys.stream().distinct().count() != keys.size()) {
            throw new RecruitException(RecruitErrorCode.DUPLICATE_IMAGE_KEY);
        }
        Set<String> stored = new HashSet<>(previouslyStored);
        mediaService.getAssets(ownerType, postingId).stream().map(MediaAssetInfo::originalKey).forEach(stored::add);
        Set<String> unowned = mediaService.unownedKeys(memberId,
                keys.stream().filter(key -> !stored.contains(key)).toList());
        if (!unowned.isEmpty()) {
            throw new RecruitException(RecruitErrorCode.UNOWNED_IMAGE_KEY, String.join(", ", unowned));
        }
    }

    // === 읽기 ===

    /** media에 자산이 없으면(이 전환 이전 데이터) {@code null} — 호출자가 기존 컬럼으로 폴백한다. */
    @Transactional(readOnly = true)
    PostingImages load(MediaOwnerType ownerType, String postingId) {
        assertRecruitOwner(ownerType);
        List<MediaAssetInfo> assets = mediaService.getAssets(ownerType, postingId);
        return assets.isEmpty() ? null : PostingImages.of(assets);
    }

    /** 목록 조회용 일괄 로딩 — 자산이 없는 게시글 ID는 결과 맵에 담기지 않는다. */
    @Transactional(readOnly = true)
    Map<String, PostingImages> loadAll(MediaOwnerType ownerType, Collection<String> postingIds) {
        assertRecruitOwner(ownerType);
        return mediaService.getAssets(ownerType, postingIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> PostingImages.of(e.getValue())));
    }

    // === 헬퍼 ===

    /**
     * 게시글 상태 판정 — 기다릴 이미지가 있으면 PROCESSING이다. 전량 실패는 READY로 넘기지 않는다.
     * 콜백 경로({@code RecruitMediaEventListener})의 판정과 같은 규칙이다.
     */
    private static ImageSyncResult resultOf(List<MediaAssetInfo> assets) {
        if (assets.isEmpty()) {
            return ImageSyncResult.READY;
        }
        if (assets.stream().anyMatch(a -> a.status() == MediaProcessingStatus.PENDING)) {
            return ImageSyncResult.PROCESSING;
        }
        return assets.stream().anyMatch(a -> a.status() == MediaProcessingStatus.DONE)
                ? ImageSyncResult.READY : ImageSyncResult.PROCESSING;
    }

    /** 썸네일이 먼저, 그다음 참고 이미지 순서대로 — 목록 순서가 media의 ordinal이 된다. */
    private static List<MediaAssetSpec> specs(String thumbnail, List<String> references) {
        List<MediaAssetSpec> specs = new ArrayList<>();
        if (isPresent(thumbnail)) {
            specs.add(new MediaAssetSpec(thumbnail, RecruitImageRole.THUMBNAIL.name()));
        }
        if (references != null) {
            references.stream().filter(RecruitImageService::isPresent)
                    .forEach(key -> specs.add(new MediaAssetSpec(key, RecruitImageRole.REFERENCE.name())));
        }
        return specs;
    }

    private static void assertRecruitOwner(MediaOwnerType ownerType) {
        if (ownerType == MediaOwnerType.ARTWORK || ownerType == MediaOwnerType.ARTWORK_THUMBNAIL) {
            throw new IllegalArgumentException("recruit이 다루는 ownerType이 아닙니다: " + ownerType);
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
