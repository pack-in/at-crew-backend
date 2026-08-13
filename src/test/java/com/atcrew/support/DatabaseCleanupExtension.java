package com.atcrew.support;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SharedContainersConfig 싱글톤 MariaDB 컨테이너를 여러 테스트 클래스가 공유하면서 생기는
 * 클래스 간 데이터 오염을 막는다. 클래스의 모든 테스트가 끝나면 도메인 테이블을 전부 truncate한다.
 *
 * <p>{@code @Transactional} 롤백 방식을 쓰지 않는 이유: 일부 모듈 테스트가
 * {@code @ApplicationModuleListener}(AFTER_COMMIT 비동기 리스너) 동작을 직접 검증하는데,
 * 트랜잭션이 실제로 커밋되지 않으면 그 리스너가 아예 발동하지 않아 테스트 자체가 무의미해진다.
 */
public class DatabaseCleanupExtension implements AfterAllCallback {

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        DataSource dataSource = applicationContext.getBean(DataSource.class);

        try (Connection connection = dataSource.getConnection()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = connection.getMetaData()
                    .getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!"flyway_schema_history".equalsIgnoreCase(tableName)) {
                        tables.add(tableName);
                    }
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) {
                    statement.execute("TRUNCATE TABLE `" + table + "`");
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }
}
