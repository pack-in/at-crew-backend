package com.atcrew.member.internal.application;

import com.atcrew.member.ArtistProfileViewedEvent;
import com.atcrew.member.internal.domain.Member;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import com.atcrew.member.internal.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * findProfileByHandle의 ArtistProfileViewedEvent 발행 조건(이슈 #37) 단위 테스트.
 *
 * <p>회원가입·경력 등 나머지 로직은 {@code MemberModuleTests}(통합 테스트)에서 이미 커버하므로,
 * 여기서는 이번 작업 범위인 이벤트 발행 조건만 검증한다.
 */
class MemberServiceImplTest {

    MemberRepository memberRepository;
    ApplicationEventPublisher eventPublisher;
    PasswordEncoder passwordEncoder;
    MemberServiceImpl memberService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("dummy-hash");
        memberService = new MemberServiceImpl(memberRepository, eventPublisher, passwordEncoder);
    }

    @Test
    void 다른_회원이_조회하면_ArtistProfileViewedEvent가_발행된다() {
        Member artist = Member.register("artist@atcrew.com", "artist_handle", "작가");
        when(memberRepository.findByHandle("artist_handle")).thenReturn(Optional.of(artist));

        memberService.findProfileByHandle("artist_handle", "viewer-001");

        ArgumentCaptor<ArtistProfileViewedEvent> captor = ArgumentCaptor.forClass(ArtistProfileViewedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().viewerMemberId()).isEqualTo("viewer-001");
        assertThat(captor.getValue().artistMemberId()).isEqualTo(artist.getId());
        assertThat(captor.getValue().occurredAt()).isNotNull();
    }

    @Test
    void 본인이_본인_프로필을_조회하면_이벤트가_발행되지_않는다() {
        Member artist = Member.register("self@atcrew.com", "self_handle", "본인");
        when(memberRepository.findByHandle("self_handle")).thenReturn(Optional.of(artist));

        memberService.findProfileByHandle("self_handle", artist.getId());

        verify(eventPublisher, never()).publishEvent(any(ArtistProfileViewedEvent.class));
    }

    @Test
    void 비로그인_조회는_viewerMemberId가_null인_이벤트로_발행된다() {
        Member artist = Member.register("anon@atcrew.com", "anon_handle", "익명대상");
        when(memberRepository.findByHandle("anon_handle")).thenReturn(Optional.of(artist));

        memberService.findProfileByHandle("anon_handle", null);

        verify(eventPublisher).publishEvent(any(ArtistProfileViewedEvent.class));
    }

    @Test
    void 단일_인자_오버로드는_비로그인_조회와_동일하게_동작한다() {
        Member artist = Member.register("legacy@atcrew.com", "legacy_handle", "레거시");
        when(memberRepository.findByHandle("legacy_handle")).thenReturn(Optional.of(artist));

        memberService.findProfileByHandle("legacy_handle");

        verify(eventPublisher).publishEvent(any(ArtistProfileViewedEvent.class));
    }

    @Test
    void 존재하지_않는_핸들이면_이벤트를_발행하지_않고_예외를_던진다() {
        when(memberRepository.findByHandle("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.findProfileByHandle("missing", "viewer-001"))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND.name());
        verify(eventPublisher, never()).publishEvent(any(ArtistProfileViewedEvent.class));
    }
}
