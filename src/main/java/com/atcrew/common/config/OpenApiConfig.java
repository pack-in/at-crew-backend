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

    @Bean
    @SuppressWarnings("unchecked")
    OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components().addSchemas("ApiResponse",
                        new ObjectSchema()
                                .description("공통 응답 봉투")
                                .addProperty("code", new StringSchema()
                                        .description("응답 코드").example("SUCCESS"))
                                .addProperty("message", new StringSchema()
                                        .description("에러 메시지 (성공 시 null)").nullable(true))
                                .addProperty("data", new Schema<>()
                                        .description("응답 데이터 (에러 시 null)").nullable(true))))
                .info(new Info()
                        .title("앳크루 API")
                        .version("v1")
                        .description("앳크루 백엔드 REST API 명세"));
    }

    @Bean
    @SuppressWarnings("unchecked")
    OperationCustomizer globalErrorResponseCustomizer() {
        return (operation, handlerMethod) -> {
            Content content = new Content().addMediaType(
                    "*/*", new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)));
            ApiResponses responses = operation.getResponses();
            // 메서드 레벨 @ApiResponse가 있으면 덮어쓰지 않음
            ERROR_CODES.forEach(e -> {
                if (!responses.containsKey(e.getKey())) {
                    responses.addApiResponse(e.getKey(),
                            new ApiResponse().description(e.getValue()).content(content));
                }
            });
            return operation;
        };
    }
}
