package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴지통으로 옮긴 지 보관 기간(기본 1년)이 지난 작품을 자동으로 영구 삭제한다(#178).
 *
 * <p>기획: "휴지통으로 이동한 시각부터 1년이 지나면 사용자 확인 모달 없이 해당 원본 작품을 자동으로 영구
 * 삭제한다. 자동 영구 삭제된 작품은 복구할 수 없다."(docs/design/portfolio-snapshot-spec.md, 마이페이지_작가-R39)
 * 사용자 영구 삭제와 같은 {@link ArtworkPurger}를 거치므로 고정형 스냅샷 보존 정책이 그대로 적용된다.
 *
 * <p>한 번에 {@link #BATCH_SIZE}건만 지운다. 한 트랜잭션이 커지는 것을 막고, 설정 실수로 보관 기간이 짧아져도
 * 한 시간에 지울 수 있는 양이 제한된다. 밀린 양은 다음 실행에서 이어서 지운다.
 */
@Component
public class TrashPurgeScheduler {

    static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(TrashPurgeScheduler.class);

    private final ArtworkRepository artworkRepository;
    private final ArtworkPurger artworkPurger;
    private final Duration retention;

    TrashPurgeScheduler(ArtworkRepository artworkRepository, ArtworkPurger artworkPurger,
                        @Value("${artwork.trash.retention:P365D}") Duration retention) {
        this.artworkRepository = artworkRepository;
        this.artworkPurger = artworkPurger;
        this.retention = retention;
    }

    /** @return 이번 실행에서 영구 삭제한 작품 수 */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
    @Transactional
    public int purgeExpiredTrash() {
        Instant threshold = Instant.now().minus(retention);
        List<Artwork> expired = artworkRepository.findByStatusAndDeletedAtBefore(
                ArtworkStatus.DELETED, threshold, PageRequest.of(0, BATCH_SIZE));
        if (expired.isEmpty()) {
            return 0;
        }
        artworkPurger.purge(expired);
        log.info("휴지통 보관 기간 만료 작품 영구 삭제: count={} retention={} threshold={}",
                expired.size(), retention, threshold);
        return expired.size();
    }
}
