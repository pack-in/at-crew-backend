package com.atcrew.media.internal.persistence;

import com.atcrew.media.*;
import com.atcrew.media.internal.domain.MediaAsset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    List<MediaAsset> findByOwnerTypeAndOwnerIdOrderByOrdinalAsc(MediaOwnerType ownerType, String ownerId);
    Optional<MediaAsset> findByOwnerTypeAndOwnerIdAndOriginalKey(MediaOwnerType ownerType, String ownerId, String originalKey);
    // 교체·삭제와 Worker 콜백이 같은 행을 두고 경쟁한다 — 잠근 뒤 읽어 순서를 정한다. 콜백이 먼저면 삭제 쪽이 콜백이
    // 기록한 변형본을 보고 고아로 넘기고, 삭제가 먼저면 콜백은 행이 없다고 보고 변형본을 고아로 넘긴다.
    // 콜백은 한 행만, 교체·삭제는 한 소유자의 행을 ordinal 순으로 잠그므로 서로 기다리는 순환이 생기지 않는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MediaAsset a where a.ownerType = :ownerType and a.ownerId = :ownerId order by a.ordinal asc")
    List<MediaAsset> findByOwnerForUpdate(@Param("ownerType") MediaOwnerType ownerType, @Param("ownerId") String ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MediaAsset a where a.ownerType = :ownerType and a.ownerId = :ownerId and a.originalKey = :originalKey")
    Optional<MediaAsset> findByOwnerAndOriginalKeyForUpdate(@Param("ownerType") MediaOwnerType ownerType,
            @Param("ownerId") String ownerId, @Param("originalKey") String originalKey);
    List<MediaAsset> findByProcessingStatusAndUpdatedAtBefore(MediaProcessingStatus status, Instant threshold);
    // 관측(docs/design/observability-design.md §6) — 업로드된 지 오래됐는데 아직 처리되지 않은 자산 수.
    long countByProcessingStatusAndCreatedAtBefore(MediaProcessingStatus status, Instant threshold);
}
