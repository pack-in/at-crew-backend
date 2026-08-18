package com.atcrew.billing.internal.config;

import com.atcrew.billing.CompanyAccountPort;
import com.stripe.StripeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StripeProperties.class, BillingProperties.class})
class BillingConfig {

    private static final Logger log = LoggerFactory.getLogger(BillingConfig.class);

    /**
     * 시크릿 키가 비어 있어도 빈은 생성한다 — 로컬·테스트에서 키 없이 기동할 수 있어야 하고,
     * 실제 호출 시점에 Stripe가 인증 오류를 돌려준다.
     */
    /**
     * company 모듈이 함께 올라오지 않는 컨텍스트(billing 단독 모듈 테스트 등)를 위한 대체 구현.
     * 실제 애플리케이션에서는 company의 어댑터가 등록되므로 이 빈은 만들어지지 않는다.
     */
    @Bean
    @ConditionalOnMissingBean(CompanyAccountPort.class)
    CompanyAccountPort noCompanyAccountPort() {
        log.warn("CompanyAccountPort 구현이 없어 모든 회원을 개인 계정으로 취급합니다 — 기업 구독 차단이 동작하지 않습니다");
        return memberId -> false;
    }

    @Bean
    StripeClient stripeClient(StripeProperties properties) {
        return StripeClient.builder()
                .setApiKey(properties.secretKey() == null ? "" : properties.secretKey())
                .build();
    }
}
