package com.atcrew.common.audit;

import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.member.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 데이터 변경 주체가 행에 남는지 검증한다 (이슈 #138).
 *
 * <p>배경: 감사 정보는 {@code created_at}/{@code updated_at}으로 "언제"만 남았고 "누가"는
 * 요청 로그(MDC)에만 있어 로그 보존 기간이 지나면 사라졌다. {@code @LastModifiedBy}는 DB에 남는다.
 *
 * <p>두 경로를 모두 고정한다. 인증 주체가 있으면 그 회원 ID가, 스케줄러·비동기 리스너·결제 웹훅처럼
 * 주체가 없으면 {@code SYSTEM}이 들어가야 한다 — 후자에서 예외가 나면 그 경로의 저장이 통째로
 * 깨지므로, 이 테스트가 없으면 운영에서야 드러난다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ImportTestcontainers(SharedContainersConfig.class)
class AuditorAwareTest {

    @Autowired
    ArtworkRepository artworkRepository;

    @AfterEach
    void 시큐리티_컨텍스트를_비운다() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증된_주체가_있으면_회원_ID가_남는다() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new MemberPrincipal("member-audit-1"), null, List.of()));

        Artwork saved = artworkRepository.save(작품("author-audit-1"));

        assertThat(saved.getLastModifiedBy()).isEqualTo("member-audit-1");
    }

    @Test
    void 인증_주체가_없으면_SYSTEM으로_남는다() {
        Artwork saved = artworkRepository.save(작품("author-audit-2"));

        assertThat(saved.getLastModifiedBy()).isEqualTo(AuditorConfig.SYSTEM);
    }

    private Artwork 작품(String authorId) {
        return Artwork.create(
                authorId, "감사 주체 검증용 작품", "설명",
                List.of("raw/1.png"), 0, null,
                ImageLayoutType.VERTICAL_SCROLL, ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.values()[0]), List.of(Genre.values()[0]), null,
                List.of("태그"), AgeRating.ALL, List.of(Language.KO), Visibility.PUBLIC,
                List.of("도구"), null, null, List.of(), List.of());
    }
}
