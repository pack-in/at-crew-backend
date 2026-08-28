package com.atcrew.member.internal.web;

import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.member.AccountInfo;
import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberProfileInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.internal.web.dto.AddCareerRequest;
import com.atcrew.member.internal.web.dto.UpdateAdultContentRequest;
import com.atcrew.member.internal.web.dto.UpdateInfoRequest;
import com.atcrew.member.internal.web.dto.UpdateMarketingAgreementRequest;
import com.atcrew.member.internal.web.dto.UpdateNameRequest;
import com.atcrew.member.internal.web.dto.UpdatePostLanguagesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "회원 가입·조회·프로필 수정·경력 관리 API")
@Validated
@RestController
@RequestMapping("/api/members")
class MemberController {

    private final MemberService memberService;
    private final SecurityUtils securityUtils;

    MemberController(MemberService memberService, SecurityUtils securityUtils) {
        this.memberService = memberService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "핸들로 회원 조회", description = "@핸들로 회원 프로필을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{handle}")
    public com.atcrew.common.response.ApiResponse<MemberProfileInfo> findByHandle(
            @Parameter(description = "회원 핸들 (@ 제외)", example = "creator_kim")
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{3,30}$", message = "핸들 형식이 올바르지 않습니다") String handle) {
        return com.atcrew.common.response.ApiResponse.success(
                memberService.findProfileByHandle(handle, getOptionalMemberId()));
    }

    @Operation(summary = "이름 수정", description = "내 이름·작가명을 수정합니다. (최대 16자)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateName(@RequestBody @Valid UpdateNameRequest request) {
        memberService.updateName(securityUtils.getCurrentMemberId(), request.name());
    }

    @Operation(summary = "프로필 정보 수정", description = "구인구직 상태·활동 분야·경력·지역·슬롯·연락처·SNS·툴 등 프로필 전체를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/info")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateInfo(@RequestBody @Valid UpdateInfoRequest request) {
        memberService.updateInfo(securityUtils.getCurrentMemberId(), new UpdateInfoCommand(
                request.employmentStatus(),
                request.activityFields(), request.experienceLevel(), request.activeRegion(),
                request.totalSlotCount(), request.availableSlotCount(), request.teamExperiences(),
                request.drawingStyles(), request.workPace(),
                request.availableStartPeriod(), request.desiredRoles(), request.desiredGenres(),
                request.desiredEmploymentTypes(), request.desiredWorkLocation(),
                request.feedbackPreferences(), request.desiredMinimumGuarantee(),
                request.desiredAnnualSalary(), request.customTags(),
                request.contact(), request.sns(), request.tools(), request.timezone(), request.countryCode()));
    }

    @Operation(summary = "마케팅 수신 동의 변경",
            description = "설정 화면의 마케팅 정보 수신 동의 토글입니다. 가입 시 받은 동의를 이후에도 켜고 끌 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/marketing-agreement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMarketingAgreement(@RequestBody @Valid UpdateMarketingAgreementRequest request) {
        memberService.updateMarketingAgreement(securityUtils.getCurrentMemberId(), request.agreed());
    }

    @Operation(summary = "성인 콘텐츠 표시 설정 변경",
            description = "설정 화면의 성인 콘텐츠 표시 토글입니다. 표시 설정만 저장하며 본인 인증 여부와는 무관합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/adult-content")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateAdultContentVisible(@RequestBody @Valid UpdateAdultContentRequest request) {
        memberService.updateAdultContentVisible(securityUtils.getCurrentMemberId(), request.visible());
    }

    @Operation(summary = "게시물 언어 변경",
            description = "홈 등에 노출받을 게시물 언어를 복수 선택합니다(설정-R14). "
                    + "주 사용 언어는 항상 포함돼야 하며 해제할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "주 사용 언어 미포함(PRIMARY_LANGUAGE_CANNOT_BE_REMOVED)"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/post-languages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePostLanguages(@RequestBody @Valid UpdatePostLanguagesRequest request) {
        memberService.updatePostLanguages(securityUtils.getCurrentMemberId(), request.languages());
    }

    @Operation(summary = "내 계정 정보 조회",
            description = "설정 > 계정 정보 화면용 응답입니다. 로그인 이메일·가입 경로·언어 설정·수신 동의 토글을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/me/account")
    public com.atcrew.common.response.ApiResponse<AccountInfo> getAccount() {
        return com.atcrew.common.response.ApiResponse.success(
                memberService.getAccount(securityUtils.getCurrentMemberId()));
    }

    @Operation(summary = "내 전체 프로필 조회",
            description = "로그인 회원 본인의 전체 프로필을 조회합니다. 로그인/refresh 응답에 담기는 정보와 동일한 전체 필드를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/me")
    public com.atcrew.common.response.ApiResponse<MemberInfo> getMe() {
        return com.atcrew.common.response.ApiResponse.success(
                memberService.findById(securityUtils.getCurrentMemberId()));
    }

    @Operation(summary = "경력 추가", description = "참여작 정보를 경력으로 추가합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "경력 추가 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/me/careers")
    @ResponseStatus(HttpStatus.CREATED)
    public com.atcrew.common.response.ApiResponse<CareerEntryInfo> addCareer(@RequestBody @Valid AddCareerRequest request) {
        return com.atcrew.common.response.ApiResponse.success(
                memberService.addCareer(securityUtils.getCurrentMemberId(), new AddCareerCommand(
                        request.workTitle(), request.role(), request.startDate(),
                        request.endDate(), request.ongoing(), request.description())));
    }

    @Operation(summary = "경력 삭제", description = "등록된 경력 항목을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping("/me/careers/{careerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCareer(@Parameter(description = "경력 ID") @PathVariable String careerId) {
        memberService.deleteCareer(securityUtils.getCurrentMemberId(), careerId);
    }

    @Operation(summary = "회원 탈퇴", description = "내 계정을 비활성화(소프트 딜리트) 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "탈퇴 처리 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate() {
        memberService.deactivate(securityUtils.getCurrentMemberId());
    }

    // 공개 GET에서 로그인 여부를 판별한다 — 비로그인이면 null (artwork/company 모듈과 동일 패턴).
    private String getOptionalMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof MemberPrincipal principal) {
            return principal.memberId();
        }
        return null;
    }
}
