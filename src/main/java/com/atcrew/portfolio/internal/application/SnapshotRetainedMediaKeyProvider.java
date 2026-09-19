package com.atcrew.portfolio.internal.application;

import com.atcrew.media.RetainedMediaKeyProvider;
import com.atcrew.portfolio.internal.persistence.PortfolioItemSnapshotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 활성 고정형 스냅샷이 참조 중인 R2 key 보존 판정 (docs/design/portfolio-module-design.md §5.6).
 *
 * <p>고정형 스냅샷은 원본 작품의 R2 key를 복사하지 않고 그대로 참조한다. 원본을 영구 삭제하거나
 * 이미지를 교체하면 media가 그 key를 R2에서 지우므로, 삭제 전에 이 판정을 거쳐 스냅샷이 참조 중인
 * key를 제외해야 한다.
 *
 * <p>스냅샷마다 참조 key를 색인(portfolio_snapshot_media_keys, V41)으로 두고 후보 key로 바로 조회한다.
 * 예전에는 후보가 카드 썸네일과 일치할 때만 스냅샷을 찾았다 — 후보가 이미지 한 장의 변형본뿐이거나
 * 사용자 지정 썸네일이 빠진 목록이면 스냅샷을 못 찾아 스냅샷 이미지가 지워졌다(PR #188 코드 리뷰).
 */
@Component
class SnapshotRetainedMediaKeyProvider implements RetainedMediaKeyProvider {

    private final PortfolioItemSnapshotRepository snapshotRepository;

    SnapshotRetainedMediaKeyProvider(PortfolioItemSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> retainedKeys(Collection<String> candidateKeys) {
        if (candidateKeys == null) {
            return Set.of();
        }
        List<String> candidates = candidateKeys.stream().filter(k -> k != null && !k.isBlank()).distinct().toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }
        return snapshotRepository.findActiveReferencedKeys(candidates);
    }
}
