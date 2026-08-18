package com.atcrew.portfolio.internal.application;

import com.atcrew.member.MemberService;
import com.atcrew.portfolio.internal.exception.PortfolioErrorCode;
import com.atcrew.portfolio.internal.exception.PortfolioException;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 공유 포트폴리오 슬러그 발급 (docs/design/portfolio-module-design.md §2.6).
 *
 * <p>UUIDv7을 그대로 노출하지 않는다 — 시간 정보가 새고 열거 가능성이 생긴다. 링크를 아는 사람은 항상
 * 접근 가능한 설계이므로 추측 불가능성이 유일한 보호막이다.
 *
 * <p>{@code /portfolio/{식별자}} 경로가 회원 handle과 슬러그를 함께 해석하므로, 발급 시 슬러그 중복과
 * handle 충돌을 함께 검사하고 충돌하면 재시도한다.
 */
@Component
class ShareSlugGenerator {

    private static final int SLUG_BYTES = 16;   // base64url 인코딩 시 패딩 없이 22자
    private static final int MAX_ATTEMPTS = 3;

    private final PortfolioRepository portfolioRepository;
    private final MemberService memberService;
    private final SecureRandom secureRandom;

    ShareSlugGenerator(PortfolioRepository portfolioRepository, MemberService memberService) {
        this.portfolioRepository = portfolioRepository;
        this.memberService = memberService;
        try {
            this.secureRandom = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new PortfolioException(PortfolioErrorCode.SLUG_GENERATION_FAILED, e.getMessage());
        }
    }

    String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String slug = randomSlug();
            if (portfolioRepository.existsByShareSlug(slug) || memberService.existsByHandle(slug)) {
                continue;
            }
            return slug;
        }
        throw new PortfolioException(PortfolioErrorCode.SLUG_GENERATION_FAILED,
                "슬러그 충돌 재시도 " + MAX_ATTEMPTS + "회 실패");
    }

    private String randomSlug() {
        byte[] buf = new byte[SLUG_BYTES];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
