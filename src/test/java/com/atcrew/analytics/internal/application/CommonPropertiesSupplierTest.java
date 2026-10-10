package com.atcrew.analytics.internal.application;

import com.atcrew.billing.BillingService;
import com.atcrew.billing.PlanType;
import com.atcrew.member.Language;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 분석 이벤트 공통 속성 조립 단위 테스트(code-review 2026-10-10, account_type 추가).
 *
 * <p>account_type은 FE(lib/posthog/common-property.ts) 값 체계를 그대로 따른다 — 회원이
 * 특정되면 "creator", 아니면 "unknown". 기업 계정 구분은 FE도 안 하므로 여기서도 안 한다.
 */
class CommonPropertiesSupplierTest {

    private final MemberService memberService = mock(MemberService.class);
    private final BillingService billingService = mock(BillingService.class);
    private final Environment environment = mock(Environment.class);

    @Test
    void 회원_무관_속성은_account_type이_unknown이다() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});
        CommonPropertiesSupplier supplier = new CommonPropertiesSupplier(memberService, billingService, environment);

        Map<String, Object> properties = supplier.ofEnvironment();

        assertThat(properties)
                .containsEntry("account_type", "unknown")
                .containsEntry("app_env", "local")
                .containsEntry("schema_version", "1")
                .doesNotContainKey("plan_type")
                .doesNotContainKey("locale");
    }

    @Test
    void 회원_기준_속성은_account_type이_creator이고_plan_locale을_더한다() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});
        when(billingService.currentPlan("member-1")).thenReturn(PlanType.PRO_MONTHLY);
        when(memberService.findById("member-1")).thenReturn(memberInfo("member-1"));
        CommonPropertiesSupplier supplier = new CommonPropertiesSupplier(memberService, billingService, environment);

        Map<String, Object> properties = supplier.of("member-1");

        assertThat(properties)
                .containsEntry("account_type", "creator")
                .containsEntry("plan_type", "PRO_MONTHLY")
                .containsEntry("locale", "ko");
    }

    // 탈퇴 직후 발행되는 이벤트처럼 회원 조회가 막히는 흐름이 있다 — account_type은 그래도 "creator"다
    // (memberId로 부른 이벤트이므로 식별은 됐다는 뜻), plan_type/locale만 빠진다.
    @Test
    void 조회가_실패해도_account_type은_creator로_남고_조회_실패한_속성만_빠진다() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});
        when(billingService.currentPlan(anyString())).thenThrow(new RuntimeException("db down"));
        when(memberService.findById(anyString())).thenThrow(new RuntimeException("not found"));
        CommonPropertiesSupplier supplier = new CommonPropertiesSupplier(memberService, billingService, environment);

        Map<String, Object> properties = supplier.of("member-2");

        assertThat(properties)
                .containsEntry("account_type", "creator")
                .doesNotContainKey("plan_type")
                .doesNotContainKey("locale");
    }

    private static MemberInfo memberInfo(String id) {
        return new MemberInfo(
                id, "handle-" + id, null, null, "작가", null, List.of(), null, null, List.of(),
                List.of(), null, null, List.of(), List.of(), List.of(), null, List.of(), null, null,
                List.of(), 0, 0, null, null, null, List.of(), true, null, null, null, null,
                null, null, Language.KO, List.of(), false, false);
    }
}
