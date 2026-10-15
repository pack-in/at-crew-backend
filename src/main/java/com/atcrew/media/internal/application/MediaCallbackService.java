package com.atcrew.media.internal.application;

import com.atcrew.media.*;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaCallbackService {
    private static final Logger log = LoggerFactory.getLogger(MediaCallbackService.class);
    private final MediaAssetRepository assets;
    private final ApplicationEventPublisher events;
    private final MediaService mediaService;
    public MediaCallbackService(MediaAssetRepository assets, ApplicationEventPublisher events, MediaService mediaService) {
        this.assets = assets; this.events = events; this.mediaService = mediaService;
    }

    /**
     * 실패 사유 없이 처리한다(구버전 Worker 콜백·테스트 진입점).
     *
     * <p>두 오버로드에 모두 {@code @Transactional}이 붙어 있어야 한다 — 한쪽이 다른 쪽을 호출하는 구조로
     * 두면 자기 호출이라 프록시를 거치지 않아 트랜잭션이 걸리지 않는다. 실제로 그렇게 작성했다가
     * 상태 변경이 커밋되지 않아 비동기 리스너를 기다리는 통합 테스트가 전부 타임아웃했다.
     * 그래서 공통 로직은 아래 private 메서드에 두고 각 진입점은 프록시를 통해 들어오게 한다.
     */
    @Transactional
    public void process(MediaOwnerType ownerType, String ownerId, String imageKey, String thumbKey, String thumbAdultKey,
                 String originalAvifKey, MediaProcessingStatus status) {
        doProcess(ownerType, ownerId, imageKey, thumbKey, thumbAdultKey, originalAvifKey, null, null, status, null);
    }

    /**
     * @param failureReason Worker가 알려준 실패 사유. 저장하지 않고 로그로만 남긴다 — 용량 초과·면적 초과
     *                      (Images는 100MP를 넘기면 거부한다)·원본 없음이 여기서 갈린다. 이 값이 없으면
     *                      운영 중에는 "FAILED"라는 사실만 남아 원인을 되짚을 수 없다.
     */
    @Transactional
    public void process(MediaOwnerType ownerType, String ownerId, String imageKey, String thumbKey, String thumbAdultKey,
                 String originalAvifKey, MediaProcessingStatus status, String failureReason) {
        doProcess(ownerType, ownerId, imageKey, thumbKey, thumbAdultKey, originalAvifKey, null, null, status, failureReason);
    }

    /**
     * @param ogKey      공유 미리보기(OG) 이미지 key. 카드 썸네일 자산만 받으며 생성에 실패하면 null이다 — OG는 부가
     *                   산출물이라 썸네일 처리 결과(status)에 영향을 주지 않는다.
     * @param ogAdultKey 성인물 블러 OG 이미지 key. 부분 성공이면 한쪽만 null로 온다.
     */
    @Transactional
    public void process(MediaOwnerType ownerType, String ownerId, String imageKey, String thumbKey, String thumbAdultKey,
                 String originalAvifKey, String ogKey, String ogAdultKey, MediaProcessingStatus status,
                 String failureReason) {
        doProcess(ownerType, ownerId, imageKey, thumbKey, thumbAdultKey, originalAvifKey, ogKey, ogAdultKey, status,
                failureReason);
    }

    private void doProcess(MediaOwnerType ownerType, String ownerId, String imageKey, String thumbKey,
                 String thumbAdultKey, String originalAvifKey, String ogKey, String ogAdultKey,
                 MediaProcessingStatus status, String failureReason) {
        if (status == MediaProcessingStatus.FAILED) {
            log.warn("이미지 변환 실패: ownerType={} ownerId={} imageKey={} reason={}",
                    ownerType, ownerId, imageKey, failureReason != null ? failureReason : "(사유 미제공)");
        }
        assets.findByOwnerAndOriginalKeyForUpdate(ownerType, ownerId, imageKey).ifPresentOrElse(asset -> {
            asset.markProcessed(thumbKey, thumbAdultKey, originalAvifKey, ogKey, ogAdultKey, status);
            if (status == MediaProcessingStatus.DONE && asset.getVariantProfile() == MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR
                    && (ogKey == null || ogAdultKey == null)) {
                // 기본 이미지로 나갈 뿐 서비스는 정상이다 — 원인 추적과 백필 대상 확인용으로만 남긴다.
                log.warn("OG 이미지 누락: ownerType={} ownerId={} imageKey={} ogKey={} ogAdultKey={}",
                        ownerType, ownerId, imageKey, ogKey, ogAdultKey);
            }
            events.publishEvent(new MediaAssetProcessedEvent(ownerType, ownerId, imageKey, thumbKey, thumbAdultKey,
                    originalAvifKey, status));
        },
        // 트리거는 커밋 뒤에만 나가므로(#174) 정상 흐름에서는 일어나지 않는다. 이미지 교체·소유자 삭제 뒤 늦게
        // 도착한 옛 콜백이거나, Worker가 받은 키와 서버 기록이 어긋난 경우다. Worker가 이미 R2에 써 둔 변형본은
        // 어디서도 참조하지 않으므로 고아 큐에 넣어 OrphanImageCleanupScheduler가 지우게 한다. 원본(imageKey)은
        // 교체·영구 삭제 경로가 이미 정리 대상으로 넘겼으므로 넣지 않는다.
        () -> orphanDerivedKeys(ownerType, ownerId, imageKey, status,
                Arrays.asList(thumbKey, thumbAdultKey, originalAvifKey, ogKey, ogAdultKey)));
    }

    /**
     * OG 전용 처리(백필) 결과를 기존 자산에 붙인다. 썸네일 key·처리 상태는 건드리지 않고 이벤트도 발행하지 않는다 —
     * 작품 상태와 무관한 부가 산출물이다. 백필 도중 썸네일이 교체·삭제돼 행이 없으면 og 파일을 고아 정리로 보낸다.
     */
    @Transactional
    public void attachOg(MediaOwnerType ownerType, String ownerId, String imageKey, String ogKey, String ogAdultKey) {
        assets.findByOwnerAndOriginalKeyForUpdate(ownerType, ownerId, imageKey).ifPresentOrElse(
                asset -> asset.attachOgKeys(ogKey, ogAdultKey),
                () -> orphanDerivedKeys(ownerType, ownerId, imageKey, null, Arrays.asList(ogKey, ogAdultKey)));
    }

    private void orphanDerivedKeys(MediaOwnerType ownerType, String ownerId, String imageKey, MediaProcessingStatus status,
                                   List<String> derivedKeys) {
        log.warn("콜백 대상 자산 없음 — 변형본을 고아 정리 대상에 넣고 무시: ownerType={} ownerId={} imageKey={} status={}",
                ownerType, ownerId, imageKey, status);
        mediaService.markOrphaned(derivedKeys);
    }
}
