package com.atcrew.search.internal.application;

import com.atcrew.search.internal.domain.ArtworkSearchDocument;
import com.atcrew.search.internal.domain.RecruitSearchDocument;
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
 * 검색 인덱스 부트스트랩. {@code artworks}/{@code recruit_posts}는 실제로는 물리 인덱스가 아니라 alias다 —
 * 최초 기동 시 물리 인덱스({@code artworks_v1}/{@code recruit_posts_v1})를 만들고 그 alias를 연결한다.
 * 이후 재색인은 새 물리 인덱스를 만들어 alias를 원자적으로 전환하는 방식으로 무중단 처리한다
 * ({@link ArtworkReindexService}, {@link RecruitReindexService}, docs/design/search-module-design.md §3).
 */
@Component
class SearchIndexInitializer {

    static final String ALIAS = "artworks";
    private static final String INITIAL_INDEX = "artworks_v1";

    static final String RECRUIT_ALIAS = "recruit_posts";
    private static final String RECRUIT_INITIAL_INDEX = "recruit_posts_v1";

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final ElasticsearchOperations operations;

    SearchIndexInitializer(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @PostConstruct
    void ensureIndex() {
        ensureIndex(ALIAS, INITIAL_INDEX, ArtworkSearchDocument.class);
        ensureIndex(RECRUIT_ALIAS, RECRUIT_INITIAL_INDEX, RecruitSearchDocument.class);
    }

    private void ensureIndex(String alias, String initialIndex, Class<?> documentClass) {
        IndexOperations aliasOps = operations.indexOps(IndexCoordinates.of(alias));
        if (aliasOps.exists()) {
            return;
        }

        IndexOperations physicalOps = operations.indexOps(IndexCoordinates.of(initialIndex));
        physicalOps.create();
        physicalOps.putMapping(operations.indexOps(documentClass).createMapping());
        physicalOps.alias(new AliasActions(
                new AliasAction.Add(AliasActionParameters.builder()
                        .withIndices(initialIndex)
                        .withAliases(alias)
                        .build())));
        log.info("검색 인덱스 부트스트랩 완료: alias={} index={}", alias, initialIndex);
    }
}
