package com.atcrew.artwork.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class TrashPurgeSchedulerTest {

    // 휴지통-R04 "이동한 날부터 1년 동안 복구할 수 있어요" — 윤년을 끼어도 달력 기준 1년이어야 한다.
    // 365일로 계산하면 2027-03-01에 버린 작품이 2028-02-29에 하루 일찍 지워진다.
    @Test
    void 보관_기간은_윤년을_끼어도_달력_기준_1년이다() {
        Instant now = Instant.parse("2028-02-29T13:00:00Z");

        Instant threshold = TrashPurgeScheduler.thresholdAt(now, Period.ofYears(1));

        assertThat(threshold).isEqualTo(Instant.parse("2027-02-28T13:00:00Z"));
        Instant trashedOnMarchFirst = Instant.parse("2027-03-01T12:00:00Z");
        assertThat(trashedOnMarchFirst.isBefore(threshold)).as("아직 1년이 지나지 않았다").isFalse();
    }

    // 운영자가 단위 없이 적거나 짧게 잘못 적으면 휴지통 작품이 복구 기회 없이 사라진다 — 기동 시점에 막는다.
    @Test
    void 보관_기간이_30일보다_짧으면_기동하지_않는다() {
        assertThatIllegalStateException().isThrownBy(() -> scheduler(Period.ofDays(1)));
        assertThatIllegalStateException().isThrownBy(() -> scheduler(Period.ZERO));
        assertThatIllegalStateException().isThrownBy(() -> scheduler(Period.ofDays(-365)));
        // P1M은 2월에 적용되면 28일이다 — 기준일에 따라 통과하면 안 된다.
        assertThatIllegalStateException().isThrownBy(() -> scheduler(Period.ofMonths(1)));
        assertThatIllegalStateException().isThrownBy(() -> scheduler(Period.of(0, 2, -30)));
        // 부호가 섞이면 실제 적용 길이가 짧다 — P1Y-12M1D는 하루다.
        assertThatIllegalStateException().isThrownBy(() -> scheduler(Period.of(1, -12, 1)));
        assertThatIllegalStateException().isThrownBy(() -> scheduler(Period.of(1, -11, 0)));
    }

    @Test
    void 보관_기간이_30일_이상이면_기동한다() {
        scheduler(Period.ofDays(30));
        scheduler(Period.ofMonths(2));
        scheduler(Period.ofYears(1));
    }

    // 설정 파일에 단위 없이 "365"라고 적으면 365일로 읽혀야 한다(Duration이면 365밀리초였다).
    @Test
    void 단위_없는_설정값은_일_단위로_읽힌다() {
        Period parsed = ApplicationConversionService.getSharedInstance().convert("365", Period.class);
        assertThat(parsed).isEqualTo(Period.ofDays(365));
        assertThat(ApplicationConversionService.getSharedInstance().convert("P1Y", Period.class)).isEqualTo(Period.ofYears(1));
    }

    // 100건을 한 트랜잭션으로 묶으면 매번 실패하는 작품 하나가 배치 전체를 롤백시켜 자동 삭제가 영영 멈춘다.
    @Test
    void 한_작품이_실패해도_나머지는_지운다() {
        ArtworkRepository repository = mock(ArtworkRepository.class);
        ArtworkPurger purger = mock(ArtworkPurger.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Artwork failing = expiredArtwork(), ok = expiredArtwork();
        when(repository.findIdsByStatusAndDeletedAtBefore(eq(ArtworkStatus.DELETED), any(), any()))
                .thenReturn(List.of("failing", "ok"));
        when(repository.findById("failing")).thenReturn(Optional.of(failing));
        when(repository.findById("ok")).thenReturn(Optional.of(ok));
        doThrow(new IllegalStateException("직렬화 실패")).when(purger).purge(List.of(failing));

        int purged = new TrashPurgeScheduler(repository, purger, txManager, Period.ofYears(1)).purgeExpiredTrash();

        assertThat(purged).isEqualTo(1);
        verify(purger).purge(List.of(ok));
        verify(txManager).rollback(any());
    }

    // 목록을 뽑은 뒤 사용자가 복구했으면 지우지 않는다.
    @Test
    void 트랜잭션_안에서_다시_읽어_복구된_작품은_지우지_않는다() {
        ArtworkRepository repository = mock(ArtworkRepository.class);
        ArtworkPurger purger = mock(ArtworkPurger.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Artwork restored = mock(Artwork.class);
        when(restored.getStatus()).thenReturn(ArtworkStatus.READY);
        when(repository.findIdsByStatusAndDeletedAtBefore(eq(ArtworkStatus.DELETED), any(), any())).thenReturn(List.of("r"));
        when(repository.findById("r")).thenReturn(Optional.of(restored));

        int purged = new TrashPurgeScheduler(repository, purger, txManager, Period.ofYears(1)).purgeExpiredTrash();

        assertThat(purged).isZero();
        verify(purger, never()).purge(any());
    }

    private static Artwork expiredArtwork() {
        Artwork artwork = mock(Artwork.class);
        when(artwork.getStatus()).thenReturn(ArtworkStatus.DELETED);
        when(artwork.getDeletedAt()).thenReturn(Instant.parse("2020-01-01T00:00:00Z"));
        return artwork;
    }

    private static TrashPurgeScheduler scheduler(Period retention) {
        return new TrashPurgeScheduler(mock(ArtworkRepository.class), mock(ArtworkPurger.class),
                mock(PlatformTransactionManager.class), retention);
    }
}
