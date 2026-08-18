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

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(properties.product(BillingProduct.PRO_MONTHLY).amount()).isEqualTo(599);
        assertThat(properties.product(BillingProduct.PRO_MONTHLY).listAmount()).isEqualTo(1199);
        assertThat(properties.product(BillingProduct.JOB_POSTING).amount()).isEqualTo(9999);
        assertThat(properties.frontendBaseUrl()).isNotBlank();
    }
}
