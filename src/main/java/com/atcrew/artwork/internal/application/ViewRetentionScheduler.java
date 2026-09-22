package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.internal.domain.view.ArtworkViewerType;
import com.atcrew.artwork.internal.persistence.ArtworkViewDedupRepository;
import com.atcrew.artwork.internal.persistence.ArtworkViewEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;

/**
 * 익명 열람 기록 보관 배치(홈-R14 "익명 식별자 기록은 1년 후 집계 통계만 보관") — 매일 1회.
 *
 * <p>1년이 지난 익명 이벤트를 일별 통계에 더한 뒤 원본을 지우고, 같은 기준으로 익명 dedup 행도 지운다.
 * 세 작업은 한 트랜잭션이다 — 합산만 커밋되고 삭제가 실패하면 다음 실행이 같은 이벤트를 다시 더한다.
 * 원본이 지워진 뒤라 재실행은 더할 것이 없어 이중 합산이 생기지 않는다. 두 인스턴스가 동시에 돌면
 * 기본 격리 수준(REPEATABLE READ)의 INSERT ... SELECT가 원본 행에 락을 걸어 한쪽이 기다리거나 교착으로
 * 통째로 롤백되므로, 같은 이벤트가 두 번 더해지지 않는다.
 *
 * <p>회원 기록은 대상이 아니다 — 활성 회원은 기한 없이 보관하고, 탈퇴 시 비식별화한다(PA-09).
 * 보관 기간은 달력 기준(UTC) 1년이다({@link TrashPurgeScheduler#thresholdAt}와 같은 계산).
 */
@Component
class ViewRetentionScheduler {

    static final Period ANONYMOUS_RETENTION = Period.ofYears(1);
    private static final Logger log = LoggerFactory.getLogger(ViewRetentionScheduler.class);

    private final ArtworkViewEventRepository eventRepository;
    private final ArtworkViewDedupRepository dedupRepository;
    private final TransactionTemplate transaction;

    ViewRetentionScheduler(ArtworkViewEventRepository eventRepository,
                           ArtworkViewDedupRepository dedupRepository,
                           PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.dedupRepository = dedupRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // 매시 정각의 핫 점수 재계산과 겹치지 않는 시각에 돈다.
    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    void purgeDaily() {
        purge(Instant.now());
    }

    void purge(Instant now) {
        Instant threshold = now.atZone(ZoneOffset.UTC).minus(ANONYMOUS_RETENTION).toInstant();
        transaction.executeWithoutResult(tx -> {
            eventRepository.accumulateDailyStatsBefore(ArtworkViewerType.ANONYMOUS.name(), threshold);
            int events = eventRepository.deleteViewedBefore(ArtworkViewerType.ANONYMOUS, threshold);
            int dedups = dedupRepository.deleteCountedBefore(ArtworkViewerType.ANONYMOUS, threshold);
            if (events > 0 || dedups > 0) {
                log.info("익명 열람 기록 보관 기간 만료 정리: events={} dedupRows={} threshold={}", events, dedups, threshold);
            }
        });
    }
}
