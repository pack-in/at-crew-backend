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

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ApiError";

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components().addSchemas("ApiError", buildApiErrorSchema()))
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
                    "application/json", new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)));
            ApiResponses responses = operation.getResponses();
            Map.of(
                    "400", "입력값 유효성 오류",
                    "401", "인증 필요",
                    "404", "리소스 없음",
                    "409", "요청 충돌",
                    "500", "서버 내부 오류"
            ).forEach((code, description) ->
                    responses.addApiResponse(code, new ApiResponse().description(description).content(content)));
            return operation;
        };
    }

    private Schema<?> buildApiErrorSchema() {
        return new ObjectSchema()
                .description("에러 응답")
                .addProperty("code", new StringSchema().description("에러 코드").example("MEMBER_NOT_FOUND"))
                .addProperty("message", new StringSchema().description("에러 메시지").example("회원을 찾을 수 없습니다."))
                .required(List.of("code", "message"));
    }
}
