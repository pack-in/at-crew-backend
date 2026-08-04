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
}
