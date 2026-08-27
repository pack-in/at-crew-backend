package com.atcrew.portfolio.internal.application;

import com.atcrew.artwork.ArtworkImageInfo;
import com.atcrew.media.RetainedMediaKeyProvider;
import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import com.atcrew.portfolio.internal.persistence.PortfolioItemSnapshotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 활성 고정형 스냅샷이 참조 중인 R2 key 보존 판정 (docs/design/portfolio-module-design.md §5.6).
 *
 * <p>고정형 스냅샷은 원본 작품의 R2 key를 복사하지 않고 그대로 참조한다. 원본을 영구 삭제하거나
 * 이미지를 교체하면 media가 그 key를 R2에서 지우므로, 삭제 전에 이 판정을 거쳐 스냅샷이 참조 중인
 * key를 제외해야 한다.
 *
 * <p>후보 key와 스냅샷의 매칭은 카드 썸네일 컬럼(`thumb_key`/`thumb_adult_key`)으로 한다 —
 * 삭제 후보는 항상 한 작품의 이미지 key 전체(원본/썸네일/성인 썸네일/avif)로 들어오므로 썸네일 하나만
 * 걸려도 그 스냅샷을 찾을 수 있다. 걸린 스냅샷의 `payload_json`을 펼쳐 상세 본문 이미지 key까지 보존
 * 대상에 넣는다.
 */
@Component
class SnapshotRetainedMediaKeyProvider implements RetainedMediaKeyProvider {

    private final PortfolioItemSnapshotRepository snapshotRepository;
    private final JsonMapper jsonMapper;

    SnapshotRetainedMediaKeyProvider(PortfolioItemSnapshotRepository snapshotRepository, JsonMapper jsonMapper) {
        this.snapshotRepository = snapshotRepository;
        this.jsonMapper = jsonMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> retainedKeys(Collection<String> candidateKeys) {
        if (candidateKeys == null || candidateKeys.isEmpty()) {
            return Set.of();
        }
        Set<String> candidates = new HashSet<>();
        for (String key : candidateKeys) {
            if (key != null && !key.isBlank()) {
                candidates.add(key);
            }
        }
        if (candidates.isEmpty()) {
            return Set.of();
        }

        Set<String> retained = new HashSet<>();
        for (PortfolioItemSnapshot snapshot : snapshotRepository.findActiveByThumbnailKeys(candidates)) {
            retainIfCandidate(retained, candidates, snapshot.getThumbKey());
            retainIfCandidate(retained, candidates, snapshot.getThumbAdultKey());
            for (ArtworkImageInfo image : detailImagesOf(snapshot)) {
                retainIfCandidate(retained, candidates, image.originalKey());
                retainIfCandidate(retained, candidates, image.thumbKey());
                retainIfCandidate(retained, candidates, image.thumbAdultKey());
                retainIfCandidate(retained, candidates, image.originalAvifKey());
            }
        }
        return retained;
    }

    private List<ArtworkImageInfo> detailImagesOf(PortfolioItemSnapshot snapshot) {
        String payloadJson = snapshot.getPayloadJson();
        if (payloadJson == null || payloadJson.isBlank()) {
            return List.of();
        }
        ArtworkSnapshotPayload payload = jsonMapper.readValue(payloadJson, ArtworkSnapshotPayload.class);
        return payload.images() == null ? List.of() : payload.images();
    }

    private static void retainIfCandidate(Set<String> retained, Set<String> candidates, String key) {
        if (key != null && candidates.contains(key)) {
            retained.add(key);
        }
    }
}
