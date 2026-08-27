package com.atcrew.support;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Statement;
import java.util.List;

/**
 * 테스트 클래스가 시작하기 전에 공유 저장소를 비운다(이슈 #68).
 *
 * <p>이전에는 테스트 클래스마다 자기 Testcontainer를 띄워 격리를 얻었다. 한 번의 전체 실행에서
 * MariaDB 13개·Elasticsearch 3개가 뜨는 구성이었고, CI 러너에서 컨테이너가 준비 전에 죽으면
 * "포트를 바꿔가며 Connection refused"가 나면서 배포가 막혔다. 게다가 공유 컨텍스트 하나가 무너지면
 * Spring의 컨텍스트 실패 임계값 때문에 관련 테스트가 통째로 연쇄 실패한다(2026-08-27 86건).
 *
 * <p>그래서 컨테이너를 하나로 모으고, 그때 잃는 격리를 이 확장이 대신한다. **클래스 시작 전 한 번**
 * 비우는 것은 "클래스마다 새 컨테이너"와 의미가 같다 — 클래스는 빈 저장소에서 시작하고, 클래스 안
 * 테스트들의 상호 의존은 기존 동작 그대로 남는다. 메서드 단위 격리는 기존 테스트의 전제를 바꾸므로
 * 이번 범위에서 다루지 않는다.
 */
public class DatabaseCleanupExtension implements BeforeAllCallback {

    /** Flyway가 관리하는 이력 테이블은 지우면 안 된다 — 스키마 검증이 깨진다. */
    private static final String FLYWAY_HISTORY = "flyway_schema_history";

    /** 색인 별칭(SearchIndexInitializer). 컨텍스트 기동 시에만 생성되므로 인덱스가 아니라 문서만 지운다. */
    private static final List<String> SEARCH_ALIASES = List.of("artworks", "recruit_posts");

    @Override
    public void beforeAll(ExtensionContext context) {
        ApplicationContext springContext = SpringExtension.getApplicationContext(context);
        truncateTables(springContext);
        clearSearchIndices(springContext);
    }

    private void truncateTables(ApplicationContext context) {
        JdbcTemplate jdbc = context.getBeanProvider(JdbcTemplate.class).getIfAvailable();
        if (jdbc == null) {
            return;
        }
        // 외래 키 검사 해제는 세션 단위라, 커넥션이 바뀌면 무효가 된다 — 한 커넥션에서 전부 처리한다.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try (var rs = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables"
                                + " WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")) {
                    List<String> tables = new java.util.ArrayList<>();
                    while (rs.next()) {
                        String table = rs.getString(1);
                        if (!FLYWAY_HISTORY.equalsIgnoreCase(table)) {
                            tables.add(table);
                        }
                    }
                    for (String table : tables) {
                        statement.execute("TRUNCATE TABLE `" + table + "`");
                    }
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    private void clearSearchIndices(ApplicationContext context) {
        ElasticsearchOperations operations =
                context.getBeanProvider(ElasticsearchOperations.class).getIfAvailable();
        if (operations == null) {
            return;
        }
        for (String alias : SEARCH_ALIASES) {
            IndexCoordinates index = IndexCoordinates.of(alias);
            if (!operations.indexOps(index).exists()) {
                continue;
            }
            operations.delete(DeleteQuery.builder(Query.findAll()).build(), Object.class, index);
            operations.indexOps(index).refresh();
        }
    }
}
