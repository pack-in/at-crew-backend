package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 휴지통으로 옮긴 지 보관 기간(기본 1년)이 지난 작품을 자동으로 영구 삭제한다(#178).
 *
 * <p>기획: "휴지통으로 이동한 시각부터 1년이 지나면 사용자 확인 모달 없이 해당 원본 작품을 자동으로 영구
 * 삭제한다. 자동 영구 삭제된 작품은 복구할 수 없다."(docs/design/portfolio-snapshot-spec.md, 마이페이지_작가-R39)
 * 사용자 영구 삭제와 같은 {@link ArtworkPurger}를 거치므로 고정형 스냅샷 보존 정책이 그대로 적용된다.
 *
 * <p>한 번에 {@link #BATCH_SIZE}건까지, 오래된 것부터 <b>작품마다 별도 트랜잭션</b>으로 지운다. 한 트랜잭션으로 묶으면
 * 매번 실패하는 작품 하나가 배치 전체를 롤백시켜 자동 삭제가 영영 멈춘다 — 실패한 작품은 로그를 남기고 건너뛴다.
 * 밀린 양은 다음 실행에서 이어서 지운다.
 *
 * <p>실패한 작품은 {@link #FAILURE_BACKOFF} 동안 조회 결과에서 뺀다. 오래된 순으로 뽑으므로, 빼지 않으면 매번 실패하는
 * 작품이 {@link #BATCH_SIZE}건 쌓였을 때 배치가 그 작품들로만 채워져 뒤의 작품이 영영 지워지지 않는다. 기록은
 * 인스턴스 메모리에만 두며 재기동하면 비워진다 — 다시 시도할 뿐이라 해가 없다.
 */
@Component
public class TrashPurgeScheduler {

    static final int BATCH_SIZE = 100;
    /** 보관 기간 하한(일). 설정 실수로 휴지통 작품이 복구 기회 없이 사라지는 것을 기동 시점에 막는다. */
    static final int MIN_RETENTION_DAYS = 30;
    static final Duration FAILURE_BACKOFF = Duration.ofDays(1);
    /** 건너뛸 작품 수 상한 — 조회 크기가 BATCH_SIZE + 이 값을 넘지 않게 한다. 넘치면 가장 먼저 기록된 것부터 다시 시도한다. */
    static final int MAX_SKIPPED = 1_000;
    private static final Logger log = LoggerFactory.getLogger(TrashPurgeScheduler.class);

    private final ArtworkRepository artworkRepository;
    private final ArtworkPurger artworkPurger;
    private final Period retention;
    private final TransactionTemplate perArtwork;
    /** 최근 실패한 작품 id → 다시 시도할 시각. 접근은 synchronized인 purgeExpiredTrash 안에서만 한다. */
    private final Map<String, Instant> skipUntil = new LinkedHashMap<>();

    /**
     * 보관 기간은 {@link Period}다. 달력 기준이라 "P1Y"는 윤년을 끼어도 정확히 1년이고, 단위 없이 "365"라고 적으면
     * 365일로 읽힌다 — {@code Duration}이었을 때는 같은 값이 365밀리초로 읽혀 즉시 삭제로 이어질 수 있었다.
     */
    TrashPurgeScheduler(ArtworkRepository artworkRepository, ArtworkPurger artworkPurger,
                        PlatformTransactionManager transactionManager,
                        @Value("${artwork.trash.retention:P1Y}") Period retention) {
        if (shortestDays(retention) < MIN_RETENTION_DAYS) {
            throw new IllegalStateException("artwork.trash.retention이 너무 짧다: " + retention
                    + " (어느 달에 적용해도 최소 " + MIN_RETENTION_DAYS + "일이어야 한다). 휴지통 작품이 복구 기간 없이 영구 삭제된다.");
        }
        this.artworkRepository = artworkRepository;
        this.artworkPurger = artworkPurger;
        this.retention = retention;
        this.perArtwork = new TransactionTemplate(transactionManager);
        this.perArtwork.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return 이번 실행에서 영구 삭제한 작품 수 */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
    public synchronized int purgeExpiredTrash() {
        Instant now = Instant.now();
        Instant threshold = thresholdAt(now, retention);
        skipUntil.values().removeIf(until -> !until.isAfter(now));
        List<String> expiredIds = artworkRepository.findIdsByStatusAndDeletedAtBefore(
                        ArtworkStatus.DELETED, threshold, PageRequest.of(0, BATCH_SIZE + skipUntil.size())).stream()
                .filter(id -> !skipUntil.containsKey(id))
                .limit(BATCH_SIZE)
                .toList();
        int purged = 0;
        for (String artworkId : expiredIds) {
            try {
                Boolean done = perArtwork.execute(tx -> purgeIfStillExpired(artworkId, threshold));
                if (Boolean.TRUE.equals(done)) {
                    purged++;
                }
            } catch (RuntimeException e) {
                log.warn("휴지통 자동 영구 삭제 실패 — {} 동안 건너뛴다: artworkId={}", FAILURE_BACKOFF, artworkId, e);
                rememberFailure(artworkId, now);
            }
        }
        if (purged > 0) {
            log.info("휴지통 보관 기간 만료 작품 영구 삭제: count={} retention={} threshold={}", purged, retention, threshold);
        }
        return purged;
    }

    private void rememberFailure(String artworkId, Instant now) {
        if (skipUntil.size() >= MAX_SKIPPED) {
            skipUntil.remove(skipUntil.keySet().iterator().next());
        }
        skipUntil.put(artworkId, now.plus(FAILURE_BACKOFF));
    }

    // 목록을 뽑은 뒤 사용자가 복구했을 수 있다 — 트랜잭션 안에서 다시 읽어 여전히 만료된 휴지통 작품일 때만 지운다.
    private boolean purgeIfStillExpired(String artworkId, Instant threshold) {
        return artworkRepository.findById(artworkId)
                .filter(a -> a.getStatus() == ArtworkStatus.DELETED)
                .filter(a -> a.getDeletedAt() != null && a.getDeletedAt().isBefore(threshold))
                .map(a -> {
                    artworkPurger.purge(List.of(a));
                    return true;
                })
                .orElse(false);
    }

    /**
     * 보관 기간이 실제로 가장 짧게 적용될 때의 일수 — 1년 365일, 1개월 28일로 센다. 특정 기준일에서 재면
     * "P1M"이 31일로 통과해 놓고 2월에는 28일만 보관하게 된다.
     */
    static long shortestDays(Period retention) {
        // 부호가 섞이면(P1Y-12M1D) 성분별 합과 실제 적용 길이가 어긋난다 — Period는 년·월을 합친 뒤 일을 뺀다.
        // 음수 성분이 있으면 하한 판정에서 떨어지도록 음수를 돌려준다.
        if (retention.getYears() < 0 || retention.getMonths() < 0 || retention.getDays() < 0) {
            return -1;
        }
        return retention.getYears() * 365L + retention.getMonths() * 28L + retention.getDays();
    }

    /** now에서 보관 기간을 달력 기준(UTC)으로 뺀 시각. deletedAt이 이보다 이전이면 삭제 대상이다. */
    static Instant thresholdAt(Instant now, Period retention) {
        return now.atZone(ZoneOffset.UTC).minus(retention).toInstant();
    }
}
