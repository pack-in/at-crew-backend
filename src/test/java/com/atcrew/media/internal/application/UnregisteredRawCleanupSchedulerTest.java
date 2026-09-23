package com.atcrew.media.internal.application;

import com.atcrew.media.RetainedMediaKeyProvider;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 등록되지 않은 raw 원본 회수(#216).
 *
 * <p>media 자산이 없는 raw key 중에도 살아 있는 것이 있다 — 사용자 지정 썸네일과 자료 첨부는 media 행 없이
 * 작품 컬럼에만 있다. 잘못 지우면 사용자 이미지가 사라지고 되돌릴 수 없다.
 */
class UnregisteredRawCleanupSchedulerTest {

    private final ArtworkStoragePort storage = mock(ArtworkStoragePort.class);
    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final RetainedMediaKeyProvider retainedProvider = mock(RetainedMediaKeyProvider.class);

    @Test
    void 등록된_자산과_참조되는_키는_남기고_나머지만_지운다() {
        givenCandidates("raw/registered.png", "raw/thumbnail.png", "raw/orphan.png");
        when(assets.findExistingOriginalKeys(anyCollection())).thenReturn(List.of("raw/registered.png"));
        when(retainedProvider.retainedKeys(anyCollection())).thenReturn(Set.of("raw/thumbnail.png"));

        int count = scheduler(true).cleanUpUnregisteredRaw();

        assertThat(count).isEqualTo(1);
        verify(storage).deleteFiles(List.of("raw/orphan.png"));
    }

    // 잘못 지우면 되돌릴 수 없다 — 운영에서 로그로 대상을 확인한 뒤 삭제를 켠다.
    @Test
    void 삭제가_꺼져_있으면_기록만_한다() {
        givenCandidates("raw/orphan.png");
        when(assets.findExistingOriginalKeys(anyCollection())).thenReturn(List.of());
        when(retainedProvider.retainedKeys(anyCollection())).thenReturn(Set.of());

        int count = scheduler(false).cleanUpUnregisteredRaw();

        assertThat(count).isEqualTo(1);
        verify(storage, never()).deleteFiles(any());
    }

    @Test
    void 지울_것이_없으면_아무것도_하지_않는다() {
        givenCandidates("raw/registered.png");
        when(assets.findExistingOriginalKeys(anyCollection())).thenReturn(List.of("raw/registered.png"));
        when(retainedProvider.retainedKeys(anyCollection())).thenReturn(Set.of());

        assertThat(scheduler(true).cleanUpUnregisteredRaw()).isZero();
        verify(storage, never()).deleteFiles(any());
    }

    // 업로드 직후 저장 요청이 도착하기 전의 파일을 지우면 안 된다.
    @Test
    void 최소_경과_시간이_한_시간보다_짧으면_기동하지_않는다() {
        assertThatIllegalStateException().isThrownBy(() ->
                new UnregisteredRawCleanupScheduler(storage, assets, List.of(retainedProvider),
                        true, Duration.ofMinutes(30), 200));
    }

    private void givenCandidates(String... keys) {
        when(storage.listKeys(anyString(), any(Instant.class), anyInt())).thenReturn(List.of(keys));
    }

    private UnregisteredRawCleanupScheduler scheduler(boolean deleteEnabled) {
        return new UnregisteredRawCleanupScheduler(storage, assets, List.of(retainedProvider),
                deleteEnabled, Duration.ofDays(2), 200);
    }
}
