package com.atcrew.support;

import com.atcrew.SharedContainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * MockMvc + Spring REST Docs 통합 테스트 공통 기반 클래스.
 *
 * <p>MariaDB 컨테이너는 {@link SharedContainersConfig}에서 {@code @TestcontainersConfiguration}으로
 * 관리된다. 이 방식은 컨테이너 라이프사이클을 Spring ApplicationContext에 묶어,
 * 컨텍스트 캐싱 중에는 컨테이너가 중단되지 않도록 보장한다.
 *
 * <p>이 클래스를 상속하면:
 * <ul>
 *   <li>MariaDB Testcontainer 공유 기동 및 연결</li>
 *   <li>Spring Security 필터 체인 포함된 MockMvc 자동 구성</li>
 *   <li>Spring REST Docs 스니펫 자동 생성 ({@code build/generated-snippets})</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ExtendWith(RestDocumentationExtension.class)
@ImportTestcontainers(SharedContainersConfig.class)
public abstract class RestDocsIntegrationSupport {

    /** 요청 수행 및 REST Docs 스니펫 생성에 사용 */
    protected MockMvc mockMvc;

    /**
     * 요청 바디 직렬화 및 응답 파싱에 사용.
     * Spring Boot 4 + Modulith 환경에서 ObjectMapper 빈 주입이 불안정하므로 직접 생성.
     */
    protected final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    private WebApplicationContext context;

    /**
     * 각 테스트 전 MockMvc를 Spring Security + REST Docs 설정과 함께 초기화한다.
     * URI 설정: https://api.atcrew.co.kr:443
     */
    @BeforeEach
    void setUpRestDocs(RestDocumentationContextProvider restDoc) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .apply(documentationConfiguration(restDoc)
                        .uris()
                        .withScheme("https")
                        .withHost("api.atcrew.co.kr")
                        .withPort(443)
                        .and()
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
