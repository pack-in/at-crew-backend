package com.atcrew.search.internal.application;

import com.atcrew.search.internal.domain.ArtworkSearchDocument;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

/**
 * 검색 인덱스 부트스트랩. {@code artworks}는 실제로는 물리 인덱스가 아니라 alias다 —
 * 최초 기동 시 물리 인덱스 {@code artworks_v1}을 만들고 그 alias로 {@code artworks}를 연결한다.
 * 이후 재색인은 새 물리 인덱스를 만들어 alias를 원자적으로 전환하는 방식으로 무중단 처리한다
 * ({@link ArtworkReindexService}, docs/design/search-module-design.md §3).
 */
@Component
class SearchIndexInitializer {

    static final String ALIAS = "artworks";
    private static final String INITIAL_INDEX = "artworks_v1";

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final ElasticsearchOperations operations;

    SearchIndexInitializer(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @PostConstruct
    void ensureIndex() {
        IndexOperations aliasOps = operations.indexOps(IndexCoordinates.of(ALIAS));
        if (aliasOps.exists()) {
            return;
        }

        IndexOperations physicalOps = operations.indexOps(IndexCoordinates.of(INITIAL_INDEX));
        physicalOps.create();
        physicalOps.putMapping(operations.indexOps(ArtworkSearchDocument.class).createMapping());
        physicalOps.alias(new AliasActions(
                new AliasAction.Add(AliasActionParameters.builder()
                        .withIndices(INITIAL_INDEX)
                        .withAliases(ALIAS)
                        .build())));
        log.info("검색 인덱스 부트스트랩 완료: alias={} index={}", ALIAS, INITIAL_INDEX);
    }
}
