package com.atcrew.search.internal.application;

import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.Visibility;
import com.atcrew.member.MemberHandleChangedEvent;
import com.atcrew.search.internal.persistence.ArtworkSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * artwork 모듈이 발행하는 {@link ArtworkChangedEvent}를 수신해 ES 색인을 upsert/remove한다.
 *
 * <p>{@code @ApplicationModuleListener}를 사용하는 이유(docs/design/search-module-design.md §5.2):
 * 원본 트랜잭션 커밋 이후에 재조회해야 stale 데이터를 읽지 않는다. 재조회 기반이라 멱등하다 —
 * 중복 이벤트나 순서 뒤바뀜에도 최종 상태는 항상 재조회 시점의 실제 상태로 수렴한다.
 */
@Component
class ArtworkSearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(ArtworkSearchIndexer.class);

    private final ArtworkService artworkService;
    private final ArtworkSearchRepository searchRepository;

    ArtworkSearchIndexer(ArtworkService artworkService, ArtworkSearchRepository searchRepository) {
        this.artworkService = artworkService;
        this.searchRepository = searchRepository;
    }

    @ApplicationModuleListener
    void onArtworkChanged(ArtworkChangedEvent event) {
        try {
            reindexOne(event.artworkId());
        } catch (Exception e) {
            // ES 색인 실패가 원본 트랜잭션에 영향을 주면 안 된다 — 실패는 로그로 남기고 전체 재색인으로 복구한다
            // (docs/design/search-module-design.md §5.2 — 이벤트 퍼블리케이션 레지스트리 재시도는 이번 범위 밖).
            log.error("검색 색인 갱신 실패: artworkId={}", event.artworkId(), e);
        }
    }

    /**
     * handle 변경 시 그 작가의 작품 문서만 재색인한다(member-handle-design §4.3). 작품마다 단건 색인과 같은
     * 규칙({@link #reindexOne})을 적용하므로 비공개·차단·미완성 작품은 색인되지 않거나 제거된다.
     * 한 작품의 실패가 나머지를 막지 않도록 작품 단위로 격리한다.
     */
    @ApplicationModuleListener
    void onMemberHandleChanged(MemberHandleChangedEvent event) {
        for (String artworkId : artworkService.getArtworkIdsByAuthor(event.memberId())) {
            try {
                reindexOne(artworkId);
            } catch (Exception e) {
                log.error("handle 변경 재색인 실패: memberId={} artworkId={}", event.memberId(), artworkId, e);
            }
        }
    }

    private void reindexOne(String artworkId) {
        artworkService.getArtworkForIndexing(artworkId)
                .filter(this::isSearchable)
                .ifPresentOrElse(this::upsert, () -> remove(artworkId));
    }

    // 운영 차단된 작품은 색인에서 제외한다(마이페이지_작가-R39 — 외부 노출 즉시 중단).
    private boolean isSearchable(ArtworkInfo artwork) {
        return artwork.status() == ArtworkStatus.READY
                && artwork.visibility() == Visibility.PUBLIC
                && !artwork.blocked();
    }

    void upsert(ArtworkInfo artwork) {
        searchRepository.save(ArtworkSearchMapper.toDocument(artwork));
    }

    void remove(String artworkId) {
        if (searchRepository.existsById(artworkId)) {
            searchRepository.deleteById(artworkId);
        }
    }
}
