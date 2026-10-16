package com.atcrew.media.internal.application;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.domain.MediaAsset;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OG 이미지 백필(docs/operations/og-image-backfill.md) — OG 기능 배포 전에 처리된 카드 썸네일에 OG를 만들게 한다.
 *
 * <p>대상은 카드 썸네일 자산(ARTWORK_THUMBNAIL)만이다. 예전 STANDARD 프로필은 본문 이미지에도 썸네일을 만들었으므로
 * "썸네일 있음"만으로 고르면 OG가 필요 없는 본문 이미지까지 처리한다. 결과는 비동기 콜백으로 붙으므로, 같은 행을 다시
 * 보내지 않도록 호출자가 {@code afterId}로 이어 읽는다. 실패분은 og가 계속 비어 있어 처음부터 다시 돌리면 재시도된다.
 */
@Service
public class OgBackfillService {

    private final MediaAssetRepository assets;
    private final ImageProcessingWorker worker;

    OgBackfillService(MediaAssetRepository assets, ImageProcessingWorker worker) {
        this.assets = assets;
        this.worker = worker;
    }

    /**
     * @param queued    이번에 Worker로 보낸 건수.
     * @param remaining {@code lastId} 뒤에 남은 대상 건수 — 0이 될 때까지 {@code afterId=lastId}로 이어 부른다.
     * @param lastId    이번에 보낸 마지막 행 id. 보낸 것이 없으면 요청의 afterId 그대로다.
     */
    public record Result(int queued, long remaining, long lastId) { }

    @Transactional(readOnly = true)
    public Result backfill(int limit, long afterId) {
        List<MediaAsset> targets = assets.findOgBackfillTargets(MediaOwnerType.ARTWORK_THUMBNAIL,
                MediaProcessingStatus.DONE, afterId, PageRequest.of(0, limit));
        for (MediaAsset asset : targets) {
            worker.triggerOgAsync(asset.getOwnerType(), asset.getOwnerId(), asset.getOriginalKey(),
                    asset.getThumbKey(), asset.getThumbAdultKey());
        }
        long lastId = targets.isEmpty() ? afterId : targets.getLast().getId();
        long remaining = assets.countOgBackfillTargets(MediaOwnerType.ARTWORK_THUMBNAIL, MediaProcessingStatus.DONE,
                lastId);
        return new Result(targets.size(), remaining, lastId);
    }
}
