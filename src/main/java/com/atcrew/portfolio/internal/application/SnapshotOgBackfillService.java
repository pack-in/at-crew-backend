package com.atcrew.portfolio.internal.application;

import com.atcrew.artwork.ArtworkOgImage;
import com.atcrew.media.MediaAssetInfo;
import com.atcrew.media.MediaService;
import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import com.atcrew.portfolio.internal.persistence.PortfolioItemSnapshotRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OG 백필 2단계(docs/operations/og-image-backfill.md) — OG 기능 배포 전에 만든 고정형 스냅샷에 OG를 채운다.
 *
 * <p>스냅샷은 카드 썸네일 key만 얼려 두었으므로, media 백필(1단계)이 그 썸네일 자산에 OG를 만든 뒤 썸네일 key로 OG 쌍을
 * 찾아 스냅샷이 얼린 연령 등급으로 하나를 고른다. 썸네일이 교체돼 자산 행이 없거나 OG가 아직·끝내 없으면 채우지 못하고
 * {@code unresolved}로 센다 — 그 스냅샷은 기본 이미지로 나간다(알려진 제약).
 */
@Service
public class SnapshotOgBackfillService {

    private final PortfolioItemSnapshotRepository snapshotRepository;
    private final MediaService mediaService;

    SnapshotOgBackfillService(PortfolioItemSnapshotRepository snapshotRepository, MediaService mediaService) {
        this.snapshotRepository = snapshotRepository;
        this.mediaService = mediaService;
    }

    /**
     * @param filled     이번에 OG를 채운 행 수.
     * @param unresolved 이번에 읽었지만 채우지 못한 행 수.
     * @param lastId     이번에 읽은 마지막 행 id — 다음 호출의 afterId. 읽은 행이 없으면 요청의 afterId 그대로다.
     * @param hasMore    limit만큼 읽었으면 true — 뒤에 행이 더 있을 수 있으니 afterId=lastId로 이어 부른다.
     */
    public record Result(int filled, int unresolved, long lastId, boolean hasMore) { }

    @Transactional
    public Result backfill(int limit, long afterId) {
        List<PortfolioItemSnapshot> rows = snapshotRepository.findByOgKeyIsNullAndIdGreaterThanOrderById(afterId,
                PageRequest.of(0, limit));
        Map<String, MediaAssetInfo> ogByThumbKey = mediaService.findOgKeysByThumbKeys(rows.stream()
                .map(PortfolioItemSnapshot::getThumbKey).filter(Objects::nonNull).toList());
        int filled = 0;
        for (PortfolioItemSnapshot row : rows) {
            MediaAssetInfo asset = row.getThumbKey() != null ? ogByThumbKey.get(row.getThumbKey()) : null;
            String ogKey = asset != null ? ArtworkOgImage.select(row.getAgeRating(), asset.ogKey(), asset.ogAdultKey()) : null;
            if (ogKey != null) {
                row.attachOgKey(ogKey);
                filled++;
            }
        }
        long lastId = rows.isEmpty() ? afterId : rows.getLast().getId();
        return new Result(filled, rows.size() - filled, lastId, rows.size() == limit);
    }
}
