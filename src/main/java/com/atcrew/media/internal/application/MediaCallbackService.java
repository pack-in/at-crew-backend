package com.atcrew.media.internal.application;

import com.atcrew.media.*;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
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
    public MediaCallbackService(MediaAssetRepository assets, ApplicationEventPublisher events) { this.assets = assets; this.events = events; }

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
        doProcess(ownerType, ownerId, imageKey, thumbKey, thumbAdultKey, originalAvifKey, status, null);
    }

    /**
     * @param failureReason Worker가 알려준 실패 사유. 저장하지 않고 로그로만 남긴다 — 용량 초과·면적 초과
     *                      (Images는 100MP를 넘기면 거부한다)·원본 없음이 여기서 갈린다. 이 값이 없으면
     *                      운영 중에는 "FAILED"라는 사실만 남아 원인을 되짚을 수 없다.
     */
    @Transactional
    public void process(MediaOwnerType ownerType, String ownerId, String imageKey, String thumbKey, String thumbAdultKey,
                 String originalAvifKey, MediaProcessingStatus status, String failureReason) {
        doProcess(ownerType, ownerId, imageKey, thumbKey, thumbAdultKey, originalAvifKey, status, failureReason);
    }

    private void doProcess(MediaOwnerType ownerType, String ownerId, String imageKey, String thumbKey,
                 String thumbAdultKey, String originalAvifKey, MediaProcessingStatus status, String failureReason) {
        if (status == MediaProcessingStatus.FAILED) {
            log.warn("이미지 변환 실패: ownerType={} ownerId={} imageKey={} reason={}",
                    ownerType, ownerId, imageKey, failureReason != null ? failureReason : "(사유 미제공)");
        }
        assets.findByOwnerTypeAndOwnerIdAndOriginalKey(ownerType, ownerId, imageKey).ifPresent(asset -> {
            asset.markProcessed(thumbKey, thumbAdultKey, originalAvifKey, status);
            events.publishEvent(new MediaAssetProcessedEvent(ownerType, ownerId, imageKey, thumbKey, thumbAdultKey,
                    originalAvifKey, status));
        });
    }
}
