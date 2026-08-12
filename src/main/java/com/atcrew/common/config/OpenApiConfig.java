package com.atcrew.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
class OpenApiConfig {

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ApiResponse";

    // 401은 인증이 필요한 개별 엔드포인트에서 직접 선언 (공개 엔드포인트 오문서화 방지)
    private static final List<Map.Entry<String, String>> ERROR_CODES = List.of(
            Map.entry("400", "입력값 유효성 오류"),
            Map.entry("404", "리소스 없음"),
            Map.entry("409", "요청 충돌"),
            Map.entry("500", "서버 내부 오류")
    );

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    @SuppressWarnings("unchecked")
    OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSchemas("ApiResponse",
                                new ObjectSchema()
                                        .description("공통 응답 봉투")
                                        .addProperty("code", new StringSchema()
                                                .description("응답 코드").example("SUCCESS"))
                                        .addProperty("message", new StringSchema()
                                                .description("에러 메시지 (성공 시 null)").nullable(true))
                                        .addProperty("data", new Schema<>()
                                                .description("응답 데이터 (에러 시 null)").nullable(true)))
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .info(new Info()
                        .title("앳크루 API")
                        .version("v1")
                        .description("앳크루 백엔드 REST API 명세"));
    }

    @Bean
    @SuppressWarnings("unchecked")
    OperationCustomizer globalErrorResponseCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();

            // 1) 표준 에러코드(400/404/409/500)가 메서드에 아예 선언 안 돼 있으면 채워 넣는다.
            ERROR_CODES.forEach(e -> {
                if (!responses.containsKey(e.getKey())) {
                    responses.addApiResponse(e.getKey(),
                            new ApiResponse().description(e.getValue()).content(errorContent()));
                }
            });

            // 2) 2xx가 아닌 모든 응답의 본문 스키마를 공통 에러 봉투로 강제 통일한다.
            //    컨트롤러가 content 없이 @ApiResponse(responseCode="401", description="...")만 선언하면
            //    springdoc이 메서드의 성공 응답 스키마를 그대로 재사용해버려서, Swagger UI에 "에러인데
            //    성공 응답과 같은 필드가 꽉 찬 예시"가 뜨는 문제가 생긴다(실제 발생 확인됨). 개별
            //    컨트롤러마다 content를 챙기게 하는 대신 여기서 한 번에 막는다 — 이후 어떤 컨트롤러가
            //    content 없이 에러코드만 선언해도 자동으로 올바른 스키마가 붙는다.
            responses.forEach((code, response) -> {
                if (!code.startsWith("2")) {
                    response.setContent(errorContent());
                }
            });

            return operation;
        };
    }

    private Content errorContent() {
        return new Content().addMediaType("*/*", new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)));
    }
}
