package com.atcrew.portfolio.docs;

import com.atcrew.billing.Plan;
import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.billing.internal.domain.Subscription;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * restdocs-api-spec 파이프라인 스파이크 — 포트폴리오 생성 API의 404 ARTWORK_NOT_FOUND 응답을
 * 실제로 발생시켜 REST Docs 스니펫과 openapi3 예시를 생성한다.
 *
 * <p>목적: Swagger {@code @ApiResponse.description}에 수기로 적은 에러 메시지가 실제
 * {@code PortfolioErrorCode.ARTWORK_NOT_FOUND.message}와 어긋날 수 있다는 문제를 근본적으로
 * 해결하기 위해, 테스트가 통과해야만 문서 예시가 생성되는 restdocs-api-spec을 도입할 수 있는지 검증한다.
 */
class PortfolioErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Test
    void 존재하지_않는_작품으로_포트폴리오_생성시_404_ARTWORK_NOT_FOUND() throws Exception {
        RegisteredMember member = registerProMember("작품없음유저");

        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("title", "존재하지 않는 작품 포트폴리오");
        createBody.put("reflectionType", "LIVE");
        createBody.put("artworkIds", List.of(UUID.randomUUID().toString()));

        mockMvc.perform(post("/api/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTWORK_NOT_FOUND"))
                .andDo(document("portfolio/create-shared-artwork-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 작품 ID로 공유 포트폴리오 생성 시도 — 404 ARTWORK_NOT_FOUND")));
    }

    /** 이메일 회원가입으로 액세스 토큰·회원 ID를 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "portfolio-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new RegisteredMember(
                objectMapper.readTree(body).at("/data/accessToken").asText(),
                objectMapper.readTree(body).at("/data/member/id").asText());
    }

    /** 공유 포트폴리오는 프로 전용이므로 구독 행을 직접 만들어 플랜을 승급한다. */
    private RegisteredMember registerProMember(String name) throws Exception {
        RegisteredMember member = registerMember(name);
        subscriptionRepository.save(
                Subscription.create(member.memberId(), Plan.PRO_MONTHLY, SubscriptionStatus.ACTIVE));
        return member;
    }

    private record RegisteredMember(String accessToken, String memberId) {
    }

    record RegisterRequest(
            String email,
            String password,
            String passwordConfirm,
            String name,
            boolean agreeService,
            boolean agreePrivacy,
            boolean agreeThirdParty,
            boolean agreeMarketing,
            String timezone,
            String countryCode
    ) {}
}
