package com.atcrew.analytics.internal.application;

import com.atcrew.billing.BillingService;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 모든 분석 이벤트에 함께 싣는 공통 속성을 조립한다(plans/261005-posthog-analytics-events).
 *
 * <p>{@code account_type}은 프론트 값 체계를 그대로 따른다(PH-02 회신, 2026-10-10) —
 * FE의 {@code lib/posthog/common-property.ts}는 {@code "creator"|"company"|"unknown"} 중
 * 로그인 시 무조건 {@code "creator"}, 비로그인은 {@code "unknown"}만 쓴다("MVP has creator
 * accounts only"). 저희 쪽엔 {@link com.atcrew.billing.CompanyAccountPort}로 기업 계정을 실제로
 * 가려낼 방법이 있지만, 백엔드만 정확히 "company"를 보내면 같은 기업 계정 사용자의 FE 이벤트
 * (creator)와 BE 이벤트(company)가 어긋난다 — 정확도보다 FE와의 일관성을 우선해 쓰지 않는다.
 *
 * <p>회원 조회가 실패해도 이벤트 자체는 보낸다 — 탈퇴 직후 발행되는 이벤트처럼 조회가 막히는 흐름이
 * 있어, 조회에 실패한 속성만 빼고 나머지를 전송한다.
 */
@Component
class CommonPropertiesSupplier {

    /** 속성 스키마 버전. 공통 속성 집합이 바뀌면 올린다. */
    private static final String SCHEMA_VERSION = "1";

    // FE 값 그대로 — account_type은 항상 이 둘 중 하나다(company는 FE도 안 씀).
    private static final String ACCOUNT_TYPE_CREATOR = "creator";
    private static final String ACCOUNT_TYPE_UNKNOWN = "unknown";

    private static final Logger log = LoggerFactory.getLogger(CommonPropertiesSupplier.class);

    private final MemberService memberService;
    private final BillingService billingService;
    private final String appEnv;

    CommonPropertiesSupplier(MemberService memberService, BillingService billingService, Environment environment) {
        this.memberService = memberService;
        this.billingService = billingService;
        String[] activeProfiles = environment.getActiveProfiles();
        this.appEnv = activeProfiles.length == 0 ? "default" : activeProfiles[0];
    }

    /** 회원과 무관한 공통 속성 — account_type은 FE와 맞춰 "unknown"이다. */
    Map<String, Object> ofEnvironment() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("account_type", ACCOUNT_TYPE_UNKNOWN);
        properties.put("app_env", appEnv);
        properties.put("schema_version", SCHEMA_VERSION);
        return properties;
    }

    /** 회원 기준 공통 속성 — account_type="creator" + plan_type·locale을 더한다. */
    Map<String, Object> of(String memberId) {
        Map<String, Object> properties = ofEnvironment();
        properties.put("account_type", ACCOUNT_TYPE_CREATOR);
        planType(memberId).ifPresent(planType -> properties.put("plan_type", planType));
        locale(memberId).ifPresent(locale -> properties.put("locale", locale));
        return properties;
    }

    private Optional<String> planType(String memberId) {
        try {
            return Optional.of(billingService.currentPlan(memberId).name());
        } catch (RuntimeException e) {
            log.debug("분석 공통 속성 plan_type 조회 실패: memberId={}", memberId, e);
            return Optional.empty();
        }
    }

    /** 회원의 주 사용 언어를 ISO 639-1 소문자 코드로 싣는다. 주 사용 언어가 없는 회원은 속성을 생략한다. */
    private Optional<String> locale(String memberId) {
        try {
            Language primaryLanguage = memberService.findById(memberId).primaryLanguage();
            return Optional.ofNullable(primaryLanguage)
                    .map(language -> language.name().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            log.debug("분석 공통 속성 locale 조회 실패: memberId={}", memberId, e);
            return Optional.empty();
        }
    }
}
