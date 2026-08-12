package com.atcrew.search.internal.application;

import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.Visibility;
import com.atcrew.common.response.CursorPage;
import com.atcrew.search.internal.domain.ArtworkSearchDocument;
import com.atcrew.search.internal.exception.SearchErrorCode;
import com.atcrew.search.internal.exception.SearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexInformation;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 전체 재색인 — 새 물리 인덱스를 만들어 전량 색인한 뒤 alias를 원자적으로 전환한다(무중단).
 * 최초 배포 시 백필(backfill) 용도로도 사용한다. docs/design/search-module-design.md §5.3.
 */
@Component
public class ArtworkReindexService {

    private static final Logger log = LoggerFactory.getLogger(ArtworkReindexService.class);
    private static final int BATCH_SIZE = 200;

    private final ArtworkService artworkService;
    private final ElasticsearchOperations operations;

    ArtworkReindexService(ArtworkService artworkService, ElasticsearchOperations operations) {
        this.artworkService = artworkService;
        this.operations = operations;
    }

    public void reindexAll() {
        String newIndex = SearchIndexInitializer.ALIAS + "_reindex_" + System.currentTimeMillis();
        String oldIndex = currentPhysicalIndex();

        try {
            createPhysicalIndex(newIndex);
            long count = bulkIndexAll(newIndex);
            switchAlias(oldIndex, newIndex);
            if (oldIndex != null) {
                operations.indexOps(IndexCoordinates.of(oldIndex)).delete();
            }
            log.info("전체 재색인 완료: {}건, {} -> {}", count, oldIndex, newIndex);
        } catch (Exception e) {
            // 새 인덱스는 아직 alias에 연결되지 않았으므로 서비스에 영향이 없다 — 정리만 하고 예외를 던진다
            log.error("전체 재색인 실패: newIndex={} oldIndex={}", newIndex, oldIndex, e);
            operations.indexOps(IndexCoordinates.of(newIndex)).delete();
            throw new SearchException(SearchErrorCode.REINDEX_FAILED, e);
        }
    }

    private String currentPhysicalIndex() {
        IndexCoordinates aliasCoordinates = IndexCoordinates.of(SearchIndexInitializer.ALIAS);
        IndexOperations aliasOps = operations.indexOps(aliasCoordinates);
        if (!aliasOps.exists()) {
            return null;
        }
        List<IndexInformation> info = aliasOps.getInformation(aliasCoordinates);
        return info.isEmpty() ? null : info.get(0).getName();
    }

    private void createPhysicalIndex(String indexName) {
        IndexOperations ops = operations.indexOps(IndexCoordinates.of(indexName));
        ops.create();
        ops.putMapping(operations.indexOps(ArtworkSearchDocument.class).createMapping());
    }

    private long bulkIndexAll(String indexName) {
        IndexCoordinates target = IndexCoordinates.of(indexName);
        long total = 0;
        String cursor = null;
        while (true) {
            CursorPage<ArtworkInfo> page = artworkService.getArtworksForReindex(cursor, BATCH_SIZE);
            List<ArtworkSearchDocument> docs = page.items().stream()
                    .filter(this::isSearchable)
                    .map(ArtworkSearchMapper::toDocument)
                    .toList();
            if (!docs.isEmpty()) {
                operations.save(docs, target);
                total += docs.size();
            }
            if (!page.hasNext()) break;
            cursor = page.nextCursor();
        }
        // alias 전환 직후부터 바로 검색 가능하도록 명시적으로 refresh — 기본 near-real-time(≈1s) 대기에 의존하지 않는다
        operations.indexOps(target).refresh();
        return total;
    }

    // 운영 차단된 작품은 색인에서 제외한다(마이페이지_작가-R39 — 외부 노출 즉시 중단).
    private boolean isSearchable(ArtworkInfo artwork) {
        return artwork.status() == ArtworkStatus.READY
                && artwork.visibility() == Visibility.PUBLIC
                && !artwork.blocked();
    }

    private void switchAlias(String oldIndex, String newIndex) {
        List<AliasAction> actions = new ArrayList<>();
        if (oldIndex != null) {
            actions.add(new AliasAction.Remove(AliasActionParameters.builder()
                    .withIndices(oldIndex)
                    .withAliases(SearchIndexInitializer.ALIAS)
                    .build()));
        }
        actions.add(new AliasAction.Add(AliasActionParameters.builder()
                .withIndices(newIndex)
                .withAliases(SearchIndexInitializer.ALIAS)
                .build()));
        operations.indexOps(IndexCoordinates.of(newIndex))
                .alias(new AliasActions(actions.toArray(new AliasAction[0])));
    }
}
