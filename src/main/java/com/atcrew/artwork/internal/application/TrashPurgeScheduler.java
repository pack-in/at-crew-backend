package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
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
 * <p>한 번에 {@link #BATCH_SIZE}건만 지운다. 한 트랜잭션이 커지는 것을 막고, 밀린 양은 다음 실행에서 이어서 지운다.
 */
@Component
public class TrashPurgeScheduler {

    static final int BATCH_SIZE = 100;
    /** 보관 기간 하한(일). 설정 실수로 휴지통 작품이 복구 기회 없이 사라지는 것을 기동 시점에 막는다. */
    static final int MIN_RETENTION_DAYS = 30;
    private static final Logger log = LoggerFactory.getLogger(TrashPurgeScheduler.class);

    private final ArtworkRepository artworkRepository;
    private final ArtworkPurger artworkPurger;
    private final Period retention;

    /**
     * 보관 기간은 {@link Period}다. 달력 기준이라 "P1Y"는 윤년을 끼어도 정확히 1년이고, 단위 없이 "365"라고 적으면
     * 365일로 읽힌다 — {@code Duration}이었을 때는 같은 값이 365밀리초로 읽혀 즉시 삭제로 이어질 수 있었다.
     */
    TrashPurgeScheduler(ArtworkRepository artworkRepository, ArtworkPurger artworkPurger,
                        @Value("${artwork.trash.retention:P1Y}") Period retention) {
        if (shortestDays(retention) < MIN_RETENTION_DAYS) {
            throw new IllegalStateException("artwork.trash.retention이 너무 짧다: " + retention
                    + " (어느 달에 적용해도 최소 " + MIN_RETENTION_DAYS + "일이어야 한다). 휴지통 작품이 복구 기간 없이 영구 삭제된다.");
        }
        this.artworkRepository = artworkRepository;
        this.artworkPurger = artworkPurger;
        this.retention = retention;
    }

    /** @return 이번 실행에서 영구 삭제한 작품 수 */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
    @Transactional
    public int purgeExpiredTrash() {
        Instant threshold = thresholdAt(Instant.now(), retention);
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

    /**
     * 보관 기간이 실제로 가장 짧게 적용될 때의 일수 — 1년 365일, 1개월 28일로 센다. 특정 기준일에서 재면
     * "P1M"이 31일로 통과해 놓고 2월에는 28일만 보관하게 된다.
     */
    static long shortestDays(Period retention) {
        return retention.getYears() * 365L + retention.getMonths() * 28L + retention.getDays();
    }

    /** now에서 보관 기간을 달력 기준(UTC)으로 뺀 시각. deletedAt이 이보다 이전이면 삭제 대상이다. */
    static Instant thresholdAt(Instant now, Period retention) {
        return now.atZone(ZoneOffset.UTC).minus(retention).toInstant();
    }
}
