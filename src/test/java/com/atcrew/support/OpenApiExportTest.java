package com.atcrew.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    // RestDocsIntegrationSupport가 스니펫 예시용으로 고정한 호스트(§setUpRestDocs, "api.atcrew.co.kr")가
    // springdoc의 servers 필드에도 그대로 흘러들어온다 — 스니펫 전반에 쓰이는 공용 설정이라 거기서 고치지
    // 않고, 실제 프론트가 Swagger UI "Try it out"에 쓸 이 파일에서만 실제 prod 도메인으로 덮어쓴다.
    private static final String PROD_SERVER_URL = "https://api.at-crew.com";

    @Test
    void OpenAPI_스펙을_파일로_저장한다() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(json);
        ArrayNode servers = mapper.createArrayNode();
        servers.add(mapper.createObjectNode().put("url", PROD_SERVER_URL));
        root.set("servers", servers);

        Path output = Path.of("build/openapi/openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString((JsonNode) root));
    }
}
