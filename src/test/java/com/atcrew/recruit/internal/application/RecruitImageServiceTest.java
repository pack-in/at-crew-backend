package com.atcrew.recruit.internal.application;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaService;
import com.atcrew.recruit.internal.exception.RecruitException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 게시글 이미지 제출의 소유 검증(#190). 서명이 없던 시절의 key도 계속 수정할 수 있어야 한다.
 */
class RecruitImageServiceTest {

    private final MediaService mediaService = mock(MediaService.class);
    private final RecruitImageService service = new RecruitImageService(mediaService);

    // media 자산이 없는 옛 게시글(레거시 컬럼만 있는 데이터)을 수정하면, 프론트가 그 옛 key를 그대로 다시 보낸다.
    // 그 key는 서명이 없어 소유 검증에 걸리는데, 수정 전 값이라면 통과시켜야 한다 — 아니면 소유자가 자기 게시글을
    // 영영 수정하지 못한다.
    @Test
    void 수정_전에_저장돼_있던_무서명_키는_통과시킨다() {
        when(mediaService.getAssets(any(MediaOwnerType.class), anyString())).thenReturn(List.of());
        when(mediaService.unownedKeys(anyString(), anyCollection())).thenReturn(Set.of());

        service.sync("member-1", MediaOwnerType.JOB_POSTING, "posting-1",
                "raw/legacy-thumb.png", List.of("raw/legacy-ref.png"), List.of("raw/legacy-thumb.png", "raw/legacy-ref.png"));

        // 이미 저장돼 있던 key는 검증 후보에서 빠진다 — 빈 목록으로만 물어본다.
        verify(mediaService).unownedKeys("member-1", List.of());
    }

    @Test
    void 저장된_적_없는_남의_키는_거부한다() {
        when(mediaService.getAssets(any(MediaOwnerType.class), anyString())).thenReturn(List.of());
        when(mediaService.unownedKeys(anyString(), anyCollection())).thenReturn(Set.of("raw/stolen.png"));

        assertThatThrownBy(() -> service.sync("member-1", MediaOwnerType.JOB_POSTING, "posting-1",
                "raw/stolen.png", List.of(), List.of()))
                .isInstanceOf(RecruitException.class);
        verify(mediaService, never()).syncAssets(any(), anyString(), any(), any(), any());
    }

    // 같은 key가 두 번 들어오면 콜백이 행을 특정하지 못해 그 게시글의 처리가 멈춘다. media도 거부하지만 그
    // 예외는 500이 되므로 400으로 돌려준다.
    @Test
    void 같은_키를_두_번_보내면_거부한다() {
        assertThatThrownBy(() -> service.sync("member-1", MediaOwnerType.JOB_POSTING, "posting-1",
                "raw/same.png", List.of("raw/same.png"), List.of()))
                .isInstanceOf(RecruitException.class);
        verify(mediaService, never()).syncAssets(any(), anyString(), any(), any(), any());
    }

    @Test
    void 작품_소유자_타입은_받지_않는다() {
        assertThat(MediaOwnerType.ARTWORK).isNotNull();
        assertThatThrownBy(() -> service.sync("member-1", MediaOwnerType.ARTWORK, "artwork-1",
                null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
