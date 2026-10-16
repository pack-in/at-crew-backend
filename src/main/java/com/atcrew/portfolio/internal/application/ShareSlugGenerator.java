package com.atcrew.portfolio.internal.application;

import com.atcrew.portfolio.internal.exception.PortfolioErrorCode;
import com.atcrew.portfolio.internal.exception.PortfolioException;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 공유 포트폴리오 슬러그 발급 — 전환기 한정(member-handle-design §3.3).
 *
 * <p>새 주소는 {@code /{handle}/portfolio/{publicCode}}지만, 운영 FE가 새 주소 체계를 배포하기 전까지는
 * {@code shareSlug ?? portfolio.id}로 {@code /portfolio/{식별자}} 링크를 만든다. slug를 발급하지 않으면 그 링크가
 * UUID가 돼 {@code GET /api/portfolios/shared/{identifier}}에서 404가 난다. 그래서 FE 배포 전까지는 공개 코드와 함께
 * slug도 계속 발급하고, 발급 중단은 가입 handle 필수화와 같은 시점(PA-14)에 한다.
 *
 * <p>handle 충돌은 검사하지 않는다 — 슬러그는 22자 base64url이고 handle은 4~14자라 새 handle과는 겹칠 수 없다.
 * 해석 순서도 슬러그가 먼저다({@code resolveSharedPortfolio}).
 */
@Component
class ShareSlugGenerator {

    private static final int SLUG_BYTES = 16;   // base64url 인코딩 시 패딩 없이 22자
    private static final int MAX_ATTEMPTS = 3;

    private final PortfolioRepository portfolioRepository;
    private final SecureRandom secureRandom;

    ShareSlugGenerator(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
        try {
            this.secureRandom = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SecureRandom 강한 구현을 찾지 못했다", e);
        }
    }

    String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String slug = randomSlug();
            if (!portfolioRepository.existsByShareSlug(slug)) {
                return slug;
            }
        }
        // 공개 코드와 같은 "공유 링크 생성 실패"로 답한다 — 클라이언트에는 같은 사건이다.
        throw new PortfolioException(PortfolioErrorCode.PUBLIC_CODE_GENERATION_FAILED,
                "슬러그 충돌 재시도 " + MAX_ATTEMPTS + "회 실패");
    }

    private String randomSlug() {
        byte[] buf = new byte[SLUG_BYTES];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
