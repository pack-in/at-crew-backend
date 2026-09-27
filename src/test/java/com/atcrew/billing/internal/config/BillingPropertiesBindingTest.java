package com.atcrew.billing.internal.config;

import com.atcrew.billing.BillingProduct;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상품 카탈로그 설정이 실제로 바인딩되는지 확인한다.
 *
 * <p>바인딩이 비면 요금제 API 전체가 500으로 떨어진다. `src/test/resources/application.yml`이 테스트
 * 클래스패스에서 main 설정을 통째로 대체하므로 두 파일 중 하나만 갱신하면 이 테스트가 먼저 잡아낸다.
 * 컨테이너·컨텍스트 없이 초 단위로 끝난다.
 */
class BillingPropertiesBindingTest {

    @Test
    void 상품_5종이_모두_바인딩된다() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        sources.forEach(source -> environment.getPropertySources().addFirst(source));

        BillingProperties properties = Binder.get(environment)
                .bind("billing", BillingProperties.class)
                .orElseThrow(() -> new AssertionError("billing 설정이 바인딩되지 않았습니다"));

        assertThat(properties.products()).containsOnlyKeys(BillingProduct.values());
        assertThat(properties.product(BillingProduct.PRO_MONTHLY).amount()).isEqualTo(800);
        assertThat(properties.product(BillingProduct.PRO_MONTHLY).listAmount()).isNull();
        assertThat(properties.product(BillingProduct.PRO_MONTHLY).enabled()).isTrue();
        assertThat(properties.product(BillingProduct.JOB_POSTING).amount()).isEqualTo(9999);
        assertThat(properties.frontendBaseUrl()).isNotBlank();
        assertThat(properties.trialDays()).isEqualTo(30);
    }

    @Test
    void 단건_게시_상품_3종은_비활성이다() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        sources.forEach(source -> environment.getPropertySources().addFirst(source));

        BillingProperties properties = Binder.get(environment)
                .bind("billing", BillingProperties.class)
                .orElseThrow(() -> new AssertionError("billing 설정이 바인딩되지 않았습니다"));

        // PH-08: 단건 상품 3종은 판매 중단 — 카탈로그·Checkout에서 막힌다.
        assertThat(properties.product(BillingProduct.TEAM_POSTING).enabled()).isFalse();
        assertThat(properties.product(BillingProduct.BOOST).enabled()).isFalse();
        assertThat(properties.product(BillingProduct.JOB_POSTING).enabled()).isFalse();
    }

    @Test
    void 연간_결제는_PH_09에서_재개됐다() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        sources.forEach(source -> environment.getPropertySources().addFirst(source));

        BillingProperties properties = Binder.get(environment)
                .bind("billing", BillingProperties.class)
                .orElseThrow(() -> new AssertionError("billing 설정이 바인딩되지 않았습니다"));

        // PH-09(2026-08-23): 월 $8 기준 재산정한 연 $80(정가 $96, "2개월 무료").
        assertThat(properties.product(BillingProduct.PRO_YEARLY).enabled()).isTrue();
        assertThat(properties.product(BillingProduct.PRO_YEARLY).amount()).isEqualTo(8000);
        assertThat(properties.product(BillingProduct.PRO_YEARLY).listAmount()).isEqualTo(9600);
    }

    @Test
    void 체험_일수가_없거나_Stripe_허용_범위를_벗어나면_기동에_실패한다() {
        Map<BillingProduct, BillingProperties.Product> products = Map.of();

        assertThatThrownBy(() -> new BillingProperties("http://localhost:3000", null, products))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BillingProperties("http://localhost:3000", -1, products))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BillingProperties("http://localhost:3000", 731, products))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new BillingProperties("http://localhost:3000", 0, products).trialDays()).isZero();
    }
}
