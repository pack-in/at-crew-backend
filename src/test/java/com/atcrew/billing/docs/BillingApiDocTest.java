package com.atcrew.billing.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제/구독 API 문서화 — Stripe를 호출하지 않는 엔드포인트만 다룬다.
 *
 * <p>Checkout·Customer Portal 세션 생성은 실제 Stripe 호출이라 여기서 문서화하지 않는다. 빈을 대역으로
 * 바꾸면 REST Docs 계열에 Spring 컨텍스트가 하나 더 생기고, 공유 Testcontainer가 먼저 닫히는 컨텍스트를
 * 따라 종료돼 다른 문서화 테스트가 연쇄로 깨진다(실측 확인). 두 API의 응답 형태는
 * `docs/design/billing-frontend-integration.md`에, 실호출 검증은 PH-05 수동 체크리스트에 있다.
 */
class BillingApiDocTest extends RestDocsIntegrationSupport {

    @Test
    void 요금제_카탈로그_조회_문서화() throws Exception {
        mockMvc.perform(get("/api/billing/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].currency").value("USD"))
                .andDo(document("billing/get-catalog",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data[].product")
                                        .description("상품 키. MVP는 PRO_MONTHLY(Portfolio Pro)만 노출 — "
                                                + "PRO_YEARLY·TEAM_POSTING·BOOST·JOB_POSTING은 PH-08 결정으로 판매 중단"),
                                fieldWithPath("data[].amount").description("청구 금액 (USD 센트 단위 정수, 800 = $8.00)"),
                                fieldWithPath("data[].listAmount").description("취소선 정가 (센트). 할인이 없으면 null").optional(),
                                fieldWithPath("data[].currency").description("통화 (USD 단일)"),
                                fieldWithPath("data[].cta")
                                        .description("버튼 상태 (AVAILABLE·CURRENT·CHANGE·UNAVAILABLE)")
                        )));
    }

    @Test
    void 내_결제상태_조회_문서화() throws Exception {
        String accessToken = registerMemberAndGetToken("결제상태조회");

        mockMvc.perform(get("/api/billing/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("STARTER"))
                .andDo(document("billing/get-my-summary",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.plan").description("현재 플랜 (STARTER·PRO_MONTHLY·PRO_YEARLY)"),
                                fieldWithPath("data.cancelAtPeriodEnd").description("주기 종료 시 취소 예약 여부"),
                                fieldWithPath("data.balances").description("단건 게시 상품별 보유 개수")
                        )));
    }

    @Test
    void 기업_계정은_프로_플랜을_구독할_수_없다() throws Exception {
        String accessToken = registerMemberAndGetToken("기업계정");
        mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"앳크루스튜디오\"}"))
                .andExpect(status().isCreated());

        // Stripe를 호출하기 전에 막히므로 실제 결제 요청은 나가지 않는다(D14).
        mockMvc.perform(post("/api/billing/checkout-sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"PRO_MONTHLY\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_ALLOWED"))
                .andDo(document("billing/create-checkout-session-403",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())));
    }

    @Test
    void 결제이력_없는_회원의_포털_진입은_404() throws Exception {
        String accessToken = registerMemberAndGetToken("포털이력없음");

        mockMvc.perform(post("/api/billing/portal-sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                .andDo(document("billing/create-portal-session-404",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())));
    }

    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "billing-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    /** 이메일 회원가입 요청 바디 (accessToken 발급용) */
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
