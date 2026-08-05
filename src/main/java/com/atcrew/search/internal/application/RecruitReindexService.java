package com.atcrew.search.internal.application;

import com.atcrew.common.response.CursorPage;
import com.atcrew.recruit.RecruitIndexInfo;
import com.atcrew.recruit.RecruitPostType;
import com.atcrew.recruit.RecruitService;
import com.atcrew.search.internal.domain.RecruitSearchDocument;
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
 * recruit_posts 전체 재색인 — {@link ArtworkReindexService}와 동일하게 새 물리 인덱스를 만들어 전량
 * 색인한 뒤 alias를 원자적으로 전환한다(무중단). 최초 배포 시 백필(backfill) 용도로도 사용한다
 * (docs/design/search-module-design.md §5.3).
 *
 * <p>구인글·팀원모집글·구직글은 서로 다른 커서 순회 대상(RecruitService.getPostsForReindex는 유형별로
 * 개별 커서를 받는다)이라, 유형별로 순회를 마친 뒤 다음 유형으로 넘어간다.
 */
@Component
public class RecruitReindexService {

    private static final Logger log = LoggerFactory.getLogger(RecruitReindexService.class);
    private static final int BATCH_SIZE = 200;
    private static final String PUBLISHED = "PUBLISHED";

    private final RecruitService recruitService;
    private final ElasticsearchOperations operations;

    RecruitReindexService(RecruitService recruitService, ElasticsearchOperations operations) {
        this.recruitService = recruitService;
        this.operations = operations;
    }

    public void reindexAll() {
        String newIndex = SearchIndexInitializer.RECRUIT_ALIAS + "_reindex_" + System.currentTimeMillis();
        String oldIndex = currentPhysicalIndex();

        try {
            createPhysicalIndex(newIndex);
            long count = bulkIndexAll(newIndex);
            switchAlias(oldIndex, newIndex);
            if (oldIndex != null) {
                operations.indexOps(IndexCoordinates.of(oldIndex)).delete();
            }
            log.info("recruit 전체 재색인 완료: {}건, {} -> {}", count, oldIndex, newIndex);
        } catch (Exception e) {
            // 새 인덱스는 아직 alias에 연결되지 않았으므로 서비스에 영향이 없다 — 정리만 하고 예외를 던진다
            log.error("recruit 전체 재색인 실패: newIndex={} oldIndex={}", newIndex, oldIndex, e);
            operations.indexOps(IndexCoordinates.of(newIndex)).delete();
            throw new SearchException(SearchErrorCode.REINDEX_FAILED, e);
        }
    }

    private String currentPhysicalIndex() {
        IndexCoordinates aliasCoordinates = IndexCoordinates.of(SearchIndexInitializer.RECRUIT_ALIAS);
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
        ops.putMapping(operations.indexOps(RecruitSearchDocument.class).createMapping());
    }

    private long bulkIndexAll(String indexName) {
        IndexCoordinates target = IndexCoordinates.of(indexName);
        long total = 0;
        for (RecruitPostType postType : RecruitPostType.values()) {
            String cursor = null;
            while (true) {
                CursorPage<RecruitIndexInfo> page = recruitService.getPostsForReindex(postType, cursor, BATCH_SIZE);
                List<RecruitSearchDocument> docs = page.items().stream()
                        .filter(this::isSearchable)
                        .map(RecruitSearchMapper::toDocument)
                        .toList();
                if (!docs.isEmpty()) {
                    operations.save(docs, target);
                    total += docs.size();
                }
                if (!page.hasNext()) break;
                cursor = page.nextCursor();
            }
        }
        // alias 전환 직후부터 바로 검색 가능하도록 명시적으로 refresh — 기본 near-real-time(≈1s) 대기에 의존하지 않는다
        operations.indexOps(target).refresh();
        return total;
    }

    private boolean isSearchable(RecruitIndexInfo info) {
        return PUBLISHED.equals(info.status());
    }

    private void switchAlias(String oldIndex, String newIndex) {
        List<AliasAction> actions = new ArrayList<>();
        if (oldIndex != null) {
            actions.add(new AliasAction.Remove(AliasActionParameters.builder()
                    .withIndices(oldIndex)
                    .withAliases(SearchIndexInitializer.RECRUIT_ALIAS)
                    .build()));
        }
        actions.add(new AliasAction.Add(AliasActionParameters.builder()
                .withIndices(newIndex)
                .withAliases(SearchIndexInitializer.RECRUIT_ALIAS)
                .build()));
        operations.indexOps(IndexCoordinates.of(newIndex))
                .alias(new AliasActions(actions.toArray(new AliasAction[0])));
    }
}
