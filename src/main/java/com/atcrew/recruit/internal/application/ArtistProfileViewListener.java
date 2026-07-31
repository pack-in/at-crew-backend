package com.atcrew.recruit.internal.application;

import com.atcrew.member.ArtistProfileViewedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * member 모듈이 발행하는 {@link ArtistProfileViewedEvent}를 수신해 "최근 본 작가" 목록에 기록한다
 * (docs/design/recruit-module-design.md §2.7).
 *
 * <p>비로그인 조회(viewerMemberId == null)는 기록 대상이 아니므로 무시한다. 기업 계정 여부 게이팅은
 * 아직 스텁이므로(§7) {@code LikedArtistController}와 동일하게 로그인한 모든 회원의 조회를 기록한다.
 *
 * <p>{@code @ApplicationModuleListener}를 사용하는 이유(search 모듈의 {@code ArtworkSearchIndexer}와
 * 동일): 원본 트랜잭션(member 모듈의 프로필 조회) 커밋 이후 비동기로 실행돼 member 모듈의 응답 지연에
 * 영향을 주지 않고, 실패해도 원본 조회 자체는 영향받지 않는다.
 */
@Component
class ArtistProfileViewListener {

    private static final Logger log = LoggerFactory.getLogger(ArtistProfileViewListener.class);

    private final LikedArtistService likedArtistService;

    ArtistProfileViewListener(LikedArtistService likedArtistService) {
        this.likedArtistService = likedArtistService;
    }

    @ApplicationModuleListener
    void onArtistProfileViewed(ArtistProfileViewedEvent event) {
        if (event.viewerMemberId() == null) {
            return;
        }
        try {
            likedArtistService.recordArtistView(event.viewerMemberId(), event.artistMemberId());
        } catch (Exception e) {
            // 최근 본 작가 기록 실패가 member 모듈의 프로필 조회에 영향을 주면 안 된다 — 로그만 남긴다.
            log.error("최근 본 작가 기록 실패: viewerMemberId={} artistMemberId={}",
                    event.viewerMemberId(), event.artistMemberId(), e);
        }
    }
}
