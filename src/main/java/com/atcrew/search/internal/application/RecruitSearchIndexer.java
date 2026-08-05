package com.atcrew.search.internal.application;

import com.atcrew.recruit.RecruitIndexInfo;
import com.atcrew.recruit.RecruitPostChangedEvent;
import com.atcrew.recruit.RecruitService;
import com.atcrew.search.internal.persistence.RecruitSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * recruit 모듈이 발행하는 {@link RecruitPostChangedEvent}를 수신해 ES 색인을 upsert/remove한다.
 * {@link ArtworkSearchIndexer}와 동일한 이유로 {@code @ApplicationModuleListener}(원본 트랜잭션 커밋 이후,
 * 재조회 기반 멱등 처리)를 사용한다(docs/design/search-module-design.md §5.2).
 */
@Component
class RecruitSearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(RecruitSearchIndexer.class);

    private static final String PUBLISHED = "PUBLISHED";

    private final RecruitService recruitService;
    private final RecruitSearchRepository searchRepository;

    RecruitSearchIndexer(RecruitService recruitService, RecruitSearchRepository searchRepository) {
        this.recruitService = recruitService;
        this.searchRepository = searchRepository;
    }

    @ApplicationModuleListener
    void onRecruitPostChanged(RecruitPostChangedEvent event) {
        try {
            recruitService.getPostForIndexing(event.postType(), event.postId())
                    .filter(this::isSearchable)
                    .ifPresentOrElse(this::upsert, () -> remove(event.postId()));
        } catch (Exception e) {
            // ES 색인 실패가 원본 트랜잭션에 영향을 주면 안 된다 — 실패는 로그로 남기고 전체 재색인으로 복구한다
            // (docs/design/search-module-design.md §5.2 — 이벤트 퍼블리케이션 레지스트리 재시도는 이번 범위 밖).
            log.error("검색 색인 갱신 실패: postId={}", event.postId(), e);
        }
    }

    private boolean isSearchable(RecruitIndexInfo info) {
        return PUBLISHED.equals(info.status());
    }

    void upsert(RecruitIndexInfo info) {
        searchRepository.save(RecruitSearchMapper.toDocument(info));
    }

    void remove(String postId) {
        if (searchRepository.existsById(postId)) {
            searchRepository.deleteById(postId);
        }
    }
}
