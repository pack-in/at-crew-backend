package com.atcrew.portfolio;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.domain.PortfolioItem;
import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import com.atcrew.portfolio.internal.exception.PortfolioException;
import com.atcrew.portfolio.internal.persistence.PortfolioItemRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioItemSnapshotRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * portfolio 모듈 스키마·도메인 검증 (docs/design/portfolio-module-design.md §2).
 *
 * <p>서비스 동작은 {@code PortfolioServiceTests}가 담당하고, 여기서는 V17 DDL과 엔티티 매핑이
 * 일치하는지(ddl-auto=validate), 회원당 작가 페이지 1개 제약과 도메인 규칙이 성립하는지만 확인한다.
 *
 * <p>모듈이 artwork·billing·member 빈에 의존하게 되면서 STANDALONE 부트스트랩으로는 컨텍스트가
 * 뜨지 않는다 — 의존 모듈을 함께 올린다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Testcontainers
class PortfolioModuleTests {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @Autowired
    PortfolioRepository portfolioRepository;

    @Autowired
    PortfolioItemRepository portfolioItemRepository;

    @Autowired
    PortfolioItemSnapshotRepository portfolioItemSnapshotRepository;

    @Test
    void 작가_페이지_포트폴리오는_제목_없이_LIVE로_생성된다() {
        String memberId = newMemberId();

        Portfolio saved = portfolioRepository.save(Portfolio.createArtistPage(memberId));

        Portfolio found = portfolioRepository.findByOwnerMemberIdAndKind(memberId, PortfolioKind.ARTIST_PAGE)
                .orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getReflectionType()).isEqualTo(ReflectionType.LIVE);
        assertThat(found.getTitle()).isNull();
        assertThat(found.getShareSlug()).isNull();
        assertThat(found.getItemCount()).isZero();
        assertThat(found.getBlockedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    // uk_pf_owner_artist_page(owner_member_id, artist_page_key) — SHARED는 키가 null이라 제약을 받지 않는다(§2.5).
    @Test
    void 회원당_작가_페이지는_하나만_저장된다() {
        String memberId = newMemberId();
        portfolioRepository.saveAndFlush(Portfolio.createArtistPage(memberId));

        assertThatThrownBy(() -> portfolioRepository.saveAndFlush(Portfolio.createArtistPage(memberId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 공유_포트폴리오는_회원당_여러_개를_슬러그와_함께_저장할_수_있다() {
        String memberId = newMemberId();

        portfolioRepository.saveAndFlush(
                Portfolio.createShared(memberId, ReflectionType.LIVE, "포트폴리오 A", newSlug()));
        Portfolio second = portfolioRepository.saveAndFlush(
                Portfolio.createShared(memberId, ReflectionType.SNAPSHOT, "포트폴리오 B", newSlug()));

        assertThat(portfolioRepository.existsByShareSlug(second.getShareSlug())).isTrue();
        assertThat(portfolioRepository.findByShareSlug(second.getShareSlug()).orElseThrow().getTitle())
                .isEqualTo("포트폴리오 B");
    }

    @Test
    void 작가_페이지_포트폴리오는_제목을_변경할_수_없다() {
        Portfolio artistPage = Portfolio.createArtistPage(newMemberId());

        assertThatThrownBy(() -> artistPage.updateTitle("바꾼 제목"))
                .isInstanceOf(PortfolioException.class)
                .hasMessageContaining("제목을 변경할 수 없습니다");
    }

    @Test
    void 라이브_구성_행은_ordinal_순서로_조회되고_작품별로_역조회된다() {
        Portfolio portfolio = portfolioRepository.save(Portfolio.createArtistPage(newMemberId()));
        String firstArtworkId = newArtworkId();
        String secondArtworkId = newArtworkId();

        portfolioItemRepository.saveAll(List.of(
                PortfolioItem.of(portfolio.getId(), secondArtworkId, 1),
                PortfolioItem.of(portfolio.getId(), firstArtworkId, 0)));

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(portfolio.getId()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(firstArtworkId, secondArtworkId);
        assertThat(portfolioItemRepository.countByArtworkId(firstArtworkId)).isEqualTo(1);
        assertThat(portfolioItemRepository.existsByPortfolioIdAndArtworkId(portfolio.getId(), secondArtworkId))
                .isTrue();
    }

    // deleteByPortfolioId는 즉시 실행되는 벌크 DML이라 트랜잭션이 필요하다 —
    // 운영 코드에서는 @Transactional 서비스 메서드 안에서 호출된다.
    @Test
    @Transactional
    void 구성_행_전체_삭제_후_같은_ordinal로_다시_저장할_수_있다() {
        Portfolio portfolio = portfolioRepository.save(Portfolio.createArtistPage(newMemberId()));
        portfolioItemRepository.saveAll(List.of(
                PortfolioItem.of(portfolio.getId(), newArtworkId(), 0),
                PortfolioItem.of(portfolio.getId(), newArtworkId(), 1)));

        portfolioItemRepository.deleteByPortfolioId(portfolio.getId());
        String replacedArtworkId = newArtworkId();
        portfolioItemRepository.saveAll(List.of(PortfolioItem.of(portfolio.getId(), replacedArtworkId, 0)));

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(portfolio.getId()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(replacedArtworkId);
    }

    // 하이브리드 저장(§2.3) — 컬럼과 payload_json(JSON)이 함께 왕복하는지 확인한다.
    @Test
    void 고정형_스냅샷은_렌더_컬럼과_상세_JSON을_함께_보존한다() {
        Portfolio portfolio = portfolioRepository.save(
                Portfolio.createShared(newMemberId(), ReflectionType.SNAPSHOT, "고정형", newSlug()));
        portfolio.markAsSnapshot("작가", "{\"handle\":\"artist\"}");
        portfolioRepository.save(portfolio);
        Instant sourceCreatedAt = Instant.parse("2026-01-02T03:04:05Z");

        portfolioItemSnapshotRepository.save(PortfolioItemSnapshot.of(
                portfolio.getId(), 0, newArtworkId(), "작품 제목",
                "thumb/a.avif", "thumb-adult/a.avif", AgeRating.ALL, ArtworkField.ILLUSTRATION,
                sourceCreatedAt, "{\"description\":\"본문\"}"));

        PortfolioItemSnapshot found = portfolioItemSnapshotRepository
                .findByPortfolioIdOrderByOrdinal(portfolio.getId()).getFirst();
        assertThat(found.getTitle()).isEqualTo("작품 제목");
        assertThat(found.getThumbKey()).isEqualTo("thumb/a.avif");
        assertThat(found.getThumbAdultKey()).isEqualTo("thumb-adult/a.avif");
        assertThat(found.getAgeRating()).isEqualTo(AgeRating.ALL);
        assertThat(found.getArtworkField()).isEqualTo(ArtworkField.ILLUSTRATION);
        assertThat(found.getSourceCreatedAt()).isEqualTo(sourceCreatedAt);
        // JSON 컬럼은 왕복 과정에서 포맷(공백)이 그대로 보존되지 않으므로 원문 문자열 비교 대신
        // 공백을 제거하고 비교한다 — 소비 측은 항상 파싱해서 쓴다.
        assertThat(compact(found.getPayloadJson())).isEqualTo("{\"description\":\"본문\"}");

        Portfolio reloaded = portfolioRepository.findById(portfolio.getId()).orElseThrow();
        assertThat(reloaded.getSnapshotAt()).isNotNull();
        assertThat(reloaded.getSnapshotOwnerName()).isEqualTo("작가");
        assertThat(compact(reloaded.getSnapshotOwnerProfileJson())).isEqualTo("{\"handle\":\"artist\"}");
    }

    @Test
    void 차단은_최초_시점을_유지한다() {
        Portfolio portfolio = Portfolio.createShared(newMemberId(), ReflectionType.LIVE, "공유", newSlug());

        portfolio.block();
        Instant firstBlockedAt = portfolio.getBlockedAt();
        portfolio.block();

        assertThat(portfolio.getBlockedAt()).isEqualTo(firstBlockedAt);
    }

    // portfolio는 member/artwork 엔티티를 참조하지 않는다 — 식별자는 값으로만 다룬다(§1.3).
    private String newMemberId() {
        return UUID.randomUUID().toString();
    }

    private String newArtworkId() {
        return UUID.randomUUID().toString();
    }

    private String compact(String json) {
        return json.replace(" ", "");
    }

    private String newSlug() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 22);
    }
}
