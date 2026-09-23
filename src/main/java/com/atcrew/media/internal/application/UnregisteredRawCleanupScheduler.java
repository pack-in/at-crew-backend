package com.atcrew.media.internal.application;

import com.atcrew.media.RetainedMediaKeyProvider;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 업로드됐지만 어디에도 등록되지 않은 raw 원본을 회수한다(#216).
 *
 * <p>presign으로 받은 URL에 파일을 올리고 작품·게시글 저장을 하지 않으면 그 원본은 어디에도 기록되지 않는다.
 * 고아 큐는 "등록됐다가 빠진 key"만 받으므로 이 파일들은 정리 대상이 아니었다.
 *
 * <p><b>지우기 전에 참조를 확인한다.</b> media 자산이 없는 raw key 중에도 살아 있는 것이 있다 — 사용자 지정
 * 썸네일과 자료 첨부는 media 행 없이 작품 컬럼에만 있고, 고정형 스냅샷도 raw key를 참조할 수 있다. 판정은
 * {@link RetainedMediaKeyProvider} 구현들에 맡긴다(artwork·portfolio).
 *
 * <p>기본값은 <b>기록만 하고 지우지 않는다</b>({@code media.raw-cleanup.delete-enabled=false}). 운영에서 한동안
 * 로그로 대상을 확인한 뒤 켠다 — 잘못 지우면 사용자 이미지가 사라지고 되돌릴 수 없다.
 */
@Component
public class UnregisteredRawCleanupScheduler {

    private static final String RAW_PREFIX = "raw/";
    private static final Logger log = LoggerFactory.getLogger(UnregisteredRawCleanupScheduler.class);

    private final ArtworkStoragePort storagePort;
    private final MediaAssetRepository assets;
    private final List<RetainedMediaKeyProvider> retainedMediaKeyProviders;
    private final boolean deleteEnabled;
    private final Duration minimumAge;
    private final int batchSize;

    UnregisteredRawCleanupScheduler(ArtworkStoragePort storagePort, MediaAssetRepository assets,
                                    List<RetainedMediaKeyProvider> retainedMediaKeyProviders,
                                    @Value("${media.raw-cleanup.delete-enabled:false}") boolean deleteEnabled,
                                    @Value("${media.raw-cleanup.minimum-age:P2D}") Duration minimumAge,
                                    @Value("${media.raw-cleanup.batch-size:200}") int batchSize) {
        if (minimumAge.toHours() < 1) {
            // 업로드 직후 저장 요청이 도착하기 전의 파일을 지우지 않도록 하한을 둔다.
            throw new IllegalStateException("media.raw-cleanup.minimum-age는 최소 1시간이어야 한다: " + minimumAge);
        }
        this.storagePort = storagePort;
        this.assets = assets;
        this.retainedMediaKeyProviders = retainedMediaKeyProviders;
        this.deleteEnabled = deleteEnabled;
        this.minimumAge = minimumAge;
        this.batchSize = batchSize;
    }

    /** @return 이번 실행에서 정리 대상으로 판정한 key 수(기록만 하는 모드에서도 센다) */
    @Scheduled(fixedDelay = 21_600_000, initialDelay = 1_800_000)   // 6시간마다
    public int cleanUpUnregisteredRaw() {
        List<String> candidates = storagePort.listKeys(RAW_PREFIX, Instant.now().minus(minimumAge), batchSize);
        if (candidates.isEmpty()) {
            return 0;
        }
        // 한 번에 묻는다 — key마다 조회하면 후보 수만큼 쿼리가 나간다.
        Set<String> registered = new HashSet<>(assets.findExistingOriginalKeys(candidates));
        Set<String> retained = new HashSet<>();
        retainedMediaKeyProviders.forEach(provider -> retained.addAll(provider.retainedKeys(candidates)));
        List<String> unregistered = candidates.stream()
                .filter(key -> !registered.contains(key) && !retained.contains(key))
                .toList();
        if (unregistered.isEmpty()) {
            return 0;
        }
        if (!deleteEnabled) {
            log.info("등록되지 않은 raw 원본 발견(삭제는 꺼져 있다): count={} sample={}",
                    unregistered.size(), unregistered.stream().limit(5).toList());
            return unregistered.size();
        }
        storagePort.deleteFiles(unregistered);
        log.info("등록되지 않은 raw 원본 정리: count={}", unregistered.size());
        return unregistered.size();
    }
}
