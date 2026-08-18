package com.atcrew.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CI가 매 main 배포마다 OpenAPI 스펙을 정적 문서 사이트(GitHub Pages)로 퍼블리시하기 위해
 * {@code /v3/api-docs} 응답을 {@code build/openapi/openapi.json}으로 떠낸다.
 *
 * <p>springdoc은 prod 프로파일에서 보안상 꺼져 있으므로(application-prod.yml, 2026-08-07 결정) 실제
 * 배포 서버는 문서 소스가 될 수 없다 — 이 테스트가 유일한 소스다. 별도 bootRun+curl 없이 이미 있는
 * {@link RestDocsIntegrationSupport}(MariaDB Testcontainer + 전체 Spring 컨텍스트)를 그대로 재사용한다.
 */
class OpenApiExportTest extends RestDocsIntegrationSupport {

    @Test
    void OpenAPI_스펙을_파일로_저장한다() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Path output = Path.of("build/openapi/openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json);
    }
}
