package com.atcrew.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // description에 에러코드가 전혀 명시되지 않았을 때 쓰는 기본값. GlobalExceptionHandler/CommonErrorCode의
    // 실제 런타임 동작(검증 실패→COMMON_INVALID_INPUT, 미인증→UNAUTHENTICATED, 처리되지 않은 예외→
    // COMMON_INTERNAL_SERVER_ERROR)과 동일하게 맞춘다.
    private static final Map<String, Map.Entry<String, String>> DEFAULT_BY_STATUS = Map.of(
            "400", Map.entry("COMMON_INVALID_INPUT", "입력값이 올바르지 않습니다"),
            "401", Map.entry("UNAUTHENTICATED", "인증이 필요합니다"),
            "500", Map.entry("COMMON_INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다")
    );

    // "CODE — 설명" 형식 (auth/member 모듈 등)
    private static final Pattern DASH_FORMAT =
            Pattern.compile("^([A-Z][A-Z0-9_]{2,})\\s*—\\s*(.+)$", Pattern.DOTALL);
    // "CODE(설명), CODE(설명)" 형식 (artwork/bookmark 모듈 등)
    private static final Pattern CODE_FIRST = Pattern.compile("([A-Z][A-Z0-9_]{2,})\\(([^()]*)\\)");
    // "설명(CODE) 또는 설명(CODE)" 형식 (portfolio 모듈 등)
    private static final Pattern DESC_FIRST = Pattern.compile("([^,()]*?)\\(([A-Z][A-Z0-9_]{2,})\\)");

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    @SuppressWarnings("unchecked")
    OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSchemas("ApiResponse",
                                new ObjectSchema()
                                        .description("공통 응답 봉투 — 이 스키마는 형태만 나타낸다. 실제 code/message"
                                                + " 값 예시는 각 API 응답의 Example Value를 참고")
                                        .addProperty("code", new StringSchema()
                                                .description("응답 코드 — 성공 시 SUCCESS, 실패 시 에러코드 이름"))
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
                    responses.addApiResponse(e.getKey(), new ApiResponse().description(e.getValue()));
                }
            });

            // 2) 2xx가 아닌 모든 응답의 본문을 공통 에러 봉투 스키마로 통일하고, description에 적힌 에러코드를
            //    파싱해 실제 code/message 값이 채워진 example을 붙인다. 컨트롤러가 content 없이
            //    @ApiResponse(responseCode="401", description="...")만 선언하면 springdoc이 메서드의 성공
            //    응답 스키마를 그대로 재사용해버리는 문제, 그리고 그걸 막으려 공통 스키마 하나로만 고정하면
            //    모든 에러 응답이 code=SUCCESS·message=string 같은 의미 없는 예시를 공유하게 되는 문제, 둘 다
            //    이 한 곳에서 해결한다 — 개별 컨트롤러가 손댈 필요 없다.
            responses.forEach((status, response) -> {
                if (!status.startsWith("2")) {
                    response.setContent(errorContent(status, response.getDescription()));
                }
            });

            return operation;
        };
    }

    private Content errorContent(String status, String description) {
        Map<String, String> codeToMessage = parseCodes(description);
        if (codeToMessage.isEmpty()) {
            Map.Entry<String, String> fallback = DEFAULT_BY_STATUS.get(status);
            codeToMessage.put(
                    fallback != null ? fallback.getKey() : "HTTP_" + status,
                    fallback != null ? fallback.getValue()
                            : (description != null && !description.isBlank() ? description : "요청을 처리할 수 없습니다"));
        }

        MediaType mediaType = new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF));
        codeToMessage.forEach((code, message) -> mediaType.addExamples(code,
                new Example().summary(code).value(Map.of("code", code, "message", message))));

        return new Content().addMediaType("*/*", mediaType);
    }

    /** description에 섞여 있는 에러코드(들)를 code→message로 뽑아낸다. 코드가 없으면 빈 맵을 반환한다. */
    private Map<String, String> parseCodes(String description) {
        Map<String, String> pairs = new LinkedHashMap<>();
        if (description == null || description.isBlank()) {
            return pairs;
        }

        Matcher dash = DASH_FORMAT.matcher(description);
        if (dash.matches()) {
            pairs.put(dash.group(1), dash.group(2).trim());
            return pairs;
        }

        Matcher codeFirst = CODE_FIRST.matcher(description);
        while (codeFirst.find()) {
            pairs.put(codeFirst.group(1), codeFirst.group(2).trim());
        }
        if (!pairs.isEmpty()) {
            return pairs;
        }

        Matcher descFirst = DESC_FIRST.matcher(description);
        while (descFirst.find()) {
            String message = descFirst.group(1).replaceAll("^[,\\s또는·]+", "").trim();
            pairs.put(descFirst.group(2), message);
        }
        return pairs;
    }
}
