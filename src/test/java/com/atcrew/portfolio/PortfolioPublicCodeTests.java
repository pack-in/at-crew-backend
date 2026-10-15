package com.atcrew.portfolio;

import com.atcrew.SharedContainersConfig;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.id.PublicCodeGenerator;
import com.atcrew.member.MemberService;
import com.atcrew.portfolio.internal.application.PortfolioServiceImpl;
import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import com.atcrew.support.BillingTestSupport;
import com.atcrew.support.DatabaseCleanupExtension;
import com.atcrew.support.ScriptedRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공유 포트폴리오 6자 공개 코드 발급 검증 (docs/design/member-handle-design.md §3, §5).
 *
 * <p>충돌 경로를 결정적으로 재현하려고 {@link PublicCodeGenerator}를 {@link ScriptedRandom} 주입본으로 바꾼다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
@Import(PortfolioPublicCodeTests.ScriptedCodes.class)
class PortfolioPublicCodeTests {

    @TestConfiguration
    static class ScriptedCodes {
        @Bean
        ScriptedRandom scriptedRandom() {
            return new ScriptedRandom();
        }

        @Bean
        @Primary
        PublicCodeGenerator scriptedPublicCodeGenerator(ScriptedRandom scriptedRandom) {
            return new PublicCodeGenerator(scriptedRandom);
        }
    }

    @Autowired
    ScriptedRandom scriptedRandom;

    @Autowired
    PortfolioServiceImpl portfolioService;

    @Autowired
    PortfolioRepository portfolioRepository;

    @Autowired
    MemberService memberService;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void resetScript() {
        scriptedRandom.clear();
    }

    @Test
    void 최신반영형과_고정형_공유_포트폴리오는_코드를_받고_slug는_발급되지_않는다() {
        String memberId = registerProMember();

        PortfolioInfo live = portfolioService.createShared(memberId, "최신", ReflectionType.LIVE, List.of());
        PortfolioInfo snapshot = portfolioService.createShared(memberId, "고정", ReflectionType.SNAPSHOT, List.of());

        assertThat(portfolio(live.id()).getPublicCode()).matches("^[a-z0-9]{6}$");
        assertThat(portfolio(snapshot.id()).getPublicCode()).matches("^[a-z0-9]{6}$");
        assertThat(live.shareSlug()).isNull();
        assertThat(snapshot.shareSlug()).isNull();
    }

    @Test
    void 작가_페이지는_코드가_없다() {
        String memberId = registerProMember();

        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        assertThat(portfolio(artistPageId).getKind()).isEqualTo(PortfolioKind.ARTIST_PAGE);
        assertThat(portfolio(artistPageId).getPublicCode()).isNull();
    }

    @Test
    void 코드는_소유자_안에서만_유일하고_다른_작가와는_겹쳐도_된다() {
        String ownerA = registerProMember();
        String ownerB = registerProMember();
        scriptedRandom.enqueue("aaaaaa", "aaaaaa");

        PortfolioInfo first = portfolioService.createShared(ownerA, "A", ReflectionType.LIVE, List.of());
        PortfolioInfo second = portfolioService.createShared(ownerB, "B", ReflectionType.LIVE, List.of());

        assertThat(portfolio(first.id()).getPublicCode()).isEqualTo("aaaaaa");
        assertThat(portfolio(second.id()).getPublicCode()).isEqualTo("aaaaaa");
    }

    @Test
    void 복제본은_원본과_다른_새_코드를_받는다() {
        String memberId = registerProMember();
        scriptedRandom.enqueue("aaaaaa");
        PortfolioInfo source = portfolioService.createShared(memberId, "원본", ReflectionType.LIVE, List.of());

        // 복제는 프리필 조회 후 생성 API를 그대로 탄다 — 원본 코드가 다시 뽑혀도 재추첨된다.
        PortfolioDuplicationSourceInfo prefill = portfolioService.getDuplicationSource(memberId, source.id());
        scriptedRandom.enqueue("aaaaaa", "bbbbbb");
        PortfolioInfo copy = portfolioService.createShared(
                memberId, prefill.defaultTitle(), ReflectionType.SNAPSHOT, prefill.selectedArtworkIds());

        assertThat(portfolio(source.id()).getPublicCode()).isEqualTo("aaaaaa");
        assertThat(portfolio(copy.id()).getPublicCode()).isEqualTo("bbbbbb");
    }

    @Test
    void 세_번_모두_겹치면_코드_발급_실패로_끝나고_포트폴리오는_저장되지_않는다() {
        String memberId = registerProMember();
        scriptedRandom.enqueue("aaaaaa");
        portfolioService.createShared(memberId, "원본", ReflectionType.LIVE, List.of());

        scriptedRandom.enqueue("aaaaaa", "aaaaaa", "aaaaaa");
        assertThatThrownBy(() -> portfolioService.createShared(memberId, "실패", ReflectionType.LIVE, List.of()))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("PUBLIC_CODE_GENERATION_FAILED");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        assertThat(portfolioRepository.findByOwnerMemberId(memberId)).hasSize(1);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private Portfolio portfolio(String portfolioId) {
        return portfolioRepository.findById(portfolioId).orElseThrow();
    }

    private String registerProMember() {
        String memberId = memberService.register(
                "pfc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "pc" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "작가").id();
        BillingTestSupport.grantProPlan(subscriptionRepository, memberId);
        return memberId;
    }
}
