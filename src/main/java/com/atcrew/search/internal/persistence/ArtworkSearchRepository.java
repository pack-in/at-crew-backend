package com.atcrew.search.internal.persistence;

import com.atcrew.search.internal.domain.ArtworkSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** 단건 upsert/삭제 전용 — 다축 필터·커서 조합 검색은 {@link ArtworkSearchQueryRepository} 참고. */
public interface ArtworkSearchRepository extends ElasticsearchRepository<ArtworkSearchDocument, String> {
}
