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
 * <p>{@code account_type}은 코드베이스에 대응 개념이 없어 프론트 회신(PH-02) 전까지 싣지 않는다.
 *
 * <p>회원 조회가 실패해도 이벤트 자체는 보낸다 — 탈퇴 직후 발행되는 이벤트처럼 조회가 막히는 흐름이
 * 있어, 조회에 실패한 속성만 빼고 나머지를 전송한다.
 */
@Component
class CommonPropertiesSupplier {

    /** 속성 스키마 버전. 공통 속성 집합이 바뀌면 올린다. */
    private static final String SCHEMA_VERSION = "1";

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

    /** 회원과 무관한 공통 속성. */
    Map<String, Object> ofEnvironment() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("app_env", appEnv);
        properties.put("schema_version", SCHEMA_VERSION);
        return properties;
    }

    /** 회원 기준 공통 속성 — 환경 속성에 plan_type·locale을 더한다. */
    Map<String, Object> of(String memberId) {
        Map<String, Object> properties = ofEnvironment();
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
