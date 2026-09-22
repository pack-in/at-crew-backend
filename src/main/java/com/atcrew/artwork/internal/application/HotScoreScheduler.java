package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.internal.persistence.ArtworkHotScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * 이번 주 가장 핫한 작품의 기간 조회수를 매시 정각 다시 계산한다(홈-R03 "매시간 갱신", 홈-R14 "최근 7일").
 *
 * <p>한 트랜잭션에서 점수표를 비우고 최근 168시간 유효 열람 이벤트를 작품별로 세어 다시 채운다. 이전 결과에
 * 기대지 않으므로 몇 번을 다시 돌려도 같은 시각 기준이면 결과가 같다 — 인스턴스가 늘어 동시에 돌면 점수표
 * 행 락에서 한쪽이 기다리거나 PK 충돌로 통째로 롤백되지만, 어느 쪽이 남아도 같은 이벤트를 센 결과다
 * (ShedLock 미도입 결정, plans/260922-hot-artworks).
 * 조회 측은 일관된 읽기라 재계산 중에도 직전 커밋된 점수표를 그대로 본다.
 *
 * <p>격리 수준은 READ COMMITTED다. 기본값(REPEATABLE READ)의 INSERT ... SELECT는 원본 이벤트 행에 공유
 * 넥스트키 락을 걸어, 재계산이 도는 동안 새 열람 이벤트 INSERT가 구간 끝의 갭 락에 막힌다.
 */
@Component
class HotScoreScheduler {

    static final Duration WINDOW = Duration.ofHours(168);
    private static final Logger log = LoggerFactory.getLogger(HotScoreScheduler.class);

    private final ArtworkHotScoreRepository hotScoreRepository;
    private final TransactionTemplate transaction;

    HotScoreScheduler(ArtworkHotScoreRepository hotScoreRepository, PlatformTransactionManager transactionManager) {
        this.hotScoreRepository = hotScoreRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    void recomputeHourly() {
        recompute(Instant.now());
    }

    /** @return 기간 조회수가 있는 작품 수 */
    int recompute(Instant now) {
        Integer scored = transaction.execute(tx -> {
            hotScoreRepository.deleteAllScores();
            return hotScoreRepository.insertWindowScores(now.minus(WINDOW), now);
        });
        log.info("핫 작품 기간 조회수 재계산: artworks={} windowStart={} now={}", scored, now.minus(WINDOW), now);
        return scored != null ? scored : 0;
    }
}
