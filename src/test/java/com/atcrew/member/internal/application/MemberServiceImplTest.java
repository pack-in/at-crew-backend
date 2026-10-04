package com.atcrew.member.internal.application;

import com.atcrew.member.AccountDeletionFailedEvent;
import com.atcrew.member.ActiveRegion;
import com.atcrew.member.ActivityField;
import com.atcrew.member.ArtistProfileViewedEvent;
import com.atcrew.member.AvailableStartPeriod;
import com.atcrew.member.CustomTagInfo;
import com.atcrew.member.CustomTagType;
import com.atcrew.member.DesiredAnnualSalary;
import com.atcrew.member.DesiredEmploymentType;
import com.atcrew.member.DesiredGenre;
import com.atcrew.member.DesiredMinimumGuarantee;
import com.atcrew.member.DesiredRole;
import com.atcrew.member.DesiredWorkLocation;
import com.atcrew.member.DrawingStyle;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.FeedbackPreference;
import com.atcrew.member.ProfileSectionSavedEvent;
import com.atcrew.member.TeamExperience;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.WorkPace;
import com.atcrew.member.internal.domain.Member;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import com.atcrew.member.internal.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
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

    // ─── deactivate: account_deletion_failed (PA-04) ───────────────────

    @Test
    void 탈퇴_대상_회원이_없으면_실패_이벤트를_발행하고_예외를_다시_던진다() {
        when(memberRepository.findById("missing-member")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.deactivate("missing-member"))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND.name());

        ArgumentCaptor<AccountDeletionFailedEvent> captor =
                ArgumentCaptor.forClass(AccountDeletionFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().memberId()).isEqualTo("missing-member");
        assertThat(captor.getValue().errorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND.name());
    }

    @Test
    void 탈퇴에_성공하면_실패_이벤트를_발행하지_않는다() {
        Member member = Member.register("leave@atcrew.com", "leave_handle", "탈퇴회원");
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        memberService.deactivate(member.getId());

        verify(eventPublisher, never()).publishEvent(any(AccountDeletionFailedEvent.class));
    }

    // ─── updateInfo: profile_section_saved (PA-04) ─────────────────────

    @Test
    void 프로필_저장시_완성도와_실제로_바뀐_필드_수를_실어_발행한다() {
        Member member = Member.register("profile@atcrew.com", "profile_handle", "프로필회원");
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        // employmentStatus(기본값 PREPARING→AVAILABLE)·contact(null→값) 2개만 실제로 바뀐다
        UpdateInfoCommand command = new UpdateInfoCommand(
                EmploymentStatus.AVAILABLE, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, "010-1234-5678", null, null, null, null);

        memberService.updateInfo(member.getId(), command);

        ArgumentCaptor<ProfileSectionSavedEvent> captor = ArgumentCaptor.forClass(ProfileSectionSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProfileSectionSavedEvent event = captor.getValue();
        assertThat(event.memberId()).isEqualTo(member.getId());
        assertThat(event.sectionType()).isEqualTo("profile_info");
        assertThat(event.changedFieldCount()).isEqualTo(2);
        // employmentStatus·totalSlotCount·availableSlotCount(기본값)·contact = 4/23
        assertThat(event.completionRate()).isEqualTo(4 / 23.0);
        assertThat(event.isComplete()).isFalse();
        assertThat(event.isFirstCompletion()).isFalse();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void 아무것도_바뀌지_않으면_변경_필드_수는_0이다() {
        Member member = Member.register("notouch@atcrew.com", "notouch_handle", "무변경회원");
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        UpdateInfoCommand command = new UpdateInfoCommand(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);

        memberService.updateInfo(member.getId(), command);

        ArgumentCaptor<ProfileSectionSavedEvent> captor = ArgumentCaptor.forClass(ProfileSectionSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().changedFieldCount()).isEqualTo(0);
    }

    @Test
    void 전체_필드를_전부_채우면_처음으로_완성도_100퍼센트에_도달한다() {
        Member member = Member.register("complete@atcrew.com", "complete_handle", "완성회원");
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        UpdateInfoCommand command = new UpdateInfoCommand(
                EmploymentStatus.AVAILABLE, List.of(ActivityField.ILLUSTRATION), ExperienceLevel.THREE_TO_FOUR,
                ActiveRegion.SEOUL, 5, 3, List.of(TeamExperience.SHORT_TERM), List.of(DrawingStyle.REALISTIC),
                WorkPace.SPEED_FIRST, AvailableStartPeriod.WITHIN_ONE_WEEK, List.of(DesiredRole.ARTWORK),
                List.of(DesiredGenre.FANTASY), List.of(DesiredEmploymentType.FREELANCE),
                DesiredWorkLocation.REMOTE, List.of(FeedbackPreference.DIRECT),
                DesiredMinimumGuarantee.FROM_30_TO_40, DesiredAnnualSalary.FROM_3000_TO_3500,
                List.of(new CustomTagInfo(CustomTagType.DRAWING_STYLE, "수채화풍")),
                "010-1234-5678", "sns-handle", "clip studio", "Asia/Seoul", "KR");

        memberService.updateInfo(member.getId(), command);

        ArgumentCaptor<ProfileSectionSavedEvent> captor = ArgumentCaptor.forClass(ProfileSectionSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProfileSectionSavedEvent event = captor.getValue();
        assertThat(event.completionRate()).isEqualTo(1.0);
        assertThat(event.isComplete()).isTrue();
        assertThat(event.isFirstCompletion()).isTrue();
    }
}
