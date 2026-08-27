package com.atcrew.media.internal.persistence;

import com.atcrew.media.*;
import com.atcrew.media.internal.domain.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    List<MediaAsset> findByOwnerTypeAndOwnerIdOrderByOrdinalAsc(MediaOwnerType ownerType, String ownerId);
    Optional<MediaAsset> findByOwnerTypeAndOwnerIdAndOriginalKey(MediaOwnerType ownerType, String ownerId, String originalKey);
    List<MediaAsset> findByProcessingStatusAndUpdatedAtBefore(MediaProcessingStatus status, Instant threshold);
    // 관측(docs/design/observability-design.md §6) — 업로드된 지 오래됐는데 아직 처리되지 않은 자산 수.
    long countByProcessingStatusAndCreatedAtBefore(MediaProcessingStatus status, Instant threshold);
}
