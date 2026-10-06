package com.atcrew.member.internal.application;

import com.atcrew.member.AccountDeletionFailedEvent;
import com.atcrew.member.AddCareerCommand;
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
import static org.mockito.Mockito.times;
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
    void 기본정보와_구직정보_필드가_같이_바뀌면_섹션별로_이벤트를_나눠_발행한다() {
        Member member = Member.register("profile@atcrew.com", "profile_handle", "프로필회원");
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        // employmentStatus(구직정보, 기본값 PREPARING→AVAILABLE)·contact(기본정보, null→값) — 섹션 하나씩만 바뀜
        UpdateInfoCommand command = new UpdateInfoCommand(
                EmploymentStatus.AVAILABLE, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, "010-1234-5678", null, null, null, null);

        memberService.updateInfo(member.getId(), command);

        ArgumentCaptor<ProfileSectionSavedEvent> captor = ArgumentCaptor.forClass(ProfileSectionSavedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        List<ProfileSectionSavedEvent> events = captor.getAllValues();

        ProfileSectionSavedEvent artistInfo = events.stream()
                .filter(e -> e.sectionType().equals("artist_info")).findFirst().orElseThrow();
        assertThat(artistInfo.memberId()).isEqualTo(member.getId());
        assertThat(artistInfo.changedFieldCount()).isEqualTo(1); // contact
        assertThat(artistInfo.completionRate()).isEqualTo(1 / 10.0); // 기본정보 대상 10개 중 1개
        assertThat(artistInfo.isComplete()).isFalse();
        assertThat(artistInfo.isFirstCompletion()).isFalse();
        assertThat(artistInfo.occurredAt()).isNotNull();

        ProfileSectionSavedEvent jobInfo = events.stream()
                .filter(e -> e.sectionType().equals("job_info")).findFirst().orElseThrow();
        assertThat(jobInfo.changedFieldCount()).isEqualTo(1); // employmentStatus
        // employmentStatus는 가입 시 항상 채워지는 필드라 completion_rate 분모·분자에서 빠진다 — 0/8
        assertThat(jobInfo.completionRate()).isZero();
    }

    @Test
    void 아무것도_바뀌지_않으면_이벤트를_발행하지_않는다() {
        Member member = Member.register("notouch@atcrew.com", "notouch_handle", "무변경회원");
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        UpdateInfoCommand command = new UpdateInfoCommand(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);

        memberService.updateInfo(member.getId(), command);

        verify(eventPublisher, never()).publishEvent(any(ProfileSectionSavedEvent.class));
    }

    @Test
    void 전체_필드를_전부_채우면_두_섹션_다_처음으로_완성도_100퍼센트에_도달한다() {
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
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        for (ProfileSectionSavedEvent event : captor.getAllValues()) {
            assertThat(event.completionRate()).isEqualTo(1.0);
            assertThat(event.isComplete()).isTrue();
            assertThat(event.isFirstCompletion()).isTrue();
        }
    }

    // ─── addCareer/deleteCareer: profile_section_saved(career_info) (FE 회신 PH-02) ───

    @Test
    void 첫_경력_추가는_최초_완성으로_표시한다() {
        Member member = Member.register("career@atcrew.com", "career_handle", "경력회원");
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        AddCareerCommand command = new AddCareerCommand("참여작", "작화", java.time.LocalDate.now(), null, true, "설명");

        memberService.addCareer(member.getId(), command);

        ArgumentCaptor<ProfileSectionSavedEvent> captor = ArgumentCaptor.forClass(ProfileSectionSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProfileSectionSavedEvent event = captor.getValue();
        assertThat(event.sectionType()).isEqualTo("career_info");
        assertThat(event.completionRate()).isEqualTo(1.0);
        assertThat(event.isComplete()).isTrue();
        assertThat(event.changedFieldCount()).isEqualTo(1);
        assertThat(event.isFirstCompletion()).isTrue();
    }

    @Test
    void 두번째_경력_추가는_최초_완성이_아니다() {
        Member member = Member.register("career2@atcrew.com", "career2_handle", "경력회원2");
        member.addCareer("첫 참여작", "작화", java.time.LocalDate.now(), null, true, "설명", java.time.LocalDate.now());
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        AddCareerCommand command = new AddCareerCommand("두번째 참여작", "채색", java.time.LocalDate.now(), null, true, "설명");

        memberService.addCareer(member.getId(), command);

        ArgumentCaptor<ProfileSectionSavedEvent> captor = ArgumentCaptor.forClass(ProfileSectionSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isFirstCompletion()).isFalse();
    }

    @Test
    void 마지막_경력을_삭제하면_완성도가_0으로_떨어진다() {
        Member member = Member.register("career3@atcrew.com", "career3_handle", "경력회원3");
        var entry = member.addCareer("참여작", "작화", java.time.LocalDate.now(), null, true, "설명", java.time.LocalDate.now());
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        memberService.deleteCareer(member.getId(), entry.id());

        ArgumentCaptor<ProfileSectionSavedEvent> captor = ArgumentCaptor.forClass(ProfileSectionSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProfileSectionSavedEvent event = captor.getValue();
        assertThat(event.sectionType()).isEqualTo("career_info");
        assertThat(event.completionRate()).isZero();
        assertThat(event.isComplete()).isFalse();
        assertThat(event.isFirstCompletion()).isFalse();
    }
}
