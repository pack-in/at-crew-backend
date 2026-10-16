package com.atcrew.member.internal.web;

import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.member.AccountInfo;
import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.HandleAvailabilityInfo;
import com.atcrew.member.HandleChangeInfo;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberProfileInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.internal.web.dto.AddCareerRequest;
import com.atcrew.member.internal.web.dto.ChangeHandleRequest;
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
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "핸들로 회원 조회",
            description = "@핸들로 회원 프로필을 조회합니다. 옛 handle도 받으며(대소문자 무관), 응답의 handle은 항상 현재 "
                    + "handle입니다 — URL의 handle과 대소문자 무시 비교로 다르면 FE가 현재 주소로 리다이렉트합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON_INVALID_INPUT — 경로의 handle 형식 오류"),
            @ApiResponse(responseCode = "404", description = "MEMBER_NOT_FOUND — 한 번도 쓰인 적 없는 handle"),
            @ApiResponse(responseCode = "410", description = "MEMBER_WITHDRAWN — 탈퇴한 회원의 handle")
    })
    @GetMapping("/{handle}")
    public com.atcrew.common.response.ApiResponse<MemberProfileInfo> findByHandle(
            @Parameter(description = "회원 핸들 (@ 제외). 옛 handle 리다이렉트를 위해 하이픈 포함 50자까지 받는다",
                    example = "creator_kim")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,50}$", message = "핸들 형식이 올바르지 않습니다") String handle) {
        return com.atcrew.common.response.ApiResponse.success(
                memberService.findProfileByHandle(handle, getOptionalMemberId()));
    }

    @Operation(summary = "사용자 아이디(handle) 사용 가능 여부 확인",
            description = "가입·설정 화면의 사전 확인용입니다. 인증은 선택이며, 토큰이 있으면 본인이 소유한(현재·옛) handle은 "
                    + "사용 가능으로 판정합니다. 단 현재 handle과 대소문자까지 같으면 HANDLE_UNCHANGED(사용 불가)입니다. "
                    + "사용 불가면 errorCode에 가입·변경 API와 같은 오류 코드를 담습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "판정 성공 — available=false여도 200"),
            @ApiResponse(responseCode = "400", description = "COMMON_INVALID_INPUT — handle 파라미터가 50자 초과")
    })
    @GetMapping("/handles/availability")
    public com.atcrew.common.response.ApiResponse<HandleAvailabilityInfo> checkHandleAvailability(
            @Parameter(description = "확인할 사용자 아이디", example = "Pack_1n")
            @RequestParam(required = false) @Size(max = 50, message = "사용자 아이디가 너무 깁니다") String handle) {
        return com.atcrew.common.response.ApiResponse.success(
                memberService.checkHandleAvailability(handle, getOptionalMemberId()));
    }

    @Operation(summary = "사용자 아이디(handle) 변경",
            description = "설정-R17. 변경 성공 시점부터 30일간 다시 변경할 수 없습니다(가입 시 handle은 미산정). "
                    + "같은 값을 5분 안에 다시 보내면 성공으로 응답합니다(재시도 멱등). "
                    + "30일 제한에 걸리면 GET /api/members/me의 handleChangeAvailableAt으로 변경 가능 시각을 안내합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "HANDLE_UNCHANGED(빈 값 또는 현재와 같은 값), "
                    + "HANDLE_TOO_SHORT(4자 미만), HANDLE_TOO_LONG(15자 이상), "
                    + "HANDLE_INVALID_CHARACTERS(영문·숫자·밑줄 외 문자), HANDLE_CONSECUTIVE_UNDERSCORES(연속 밑줄), "
                    + "HANDLE_RESERVED(예약어), COMMON_INVALID_INPUT(50자 초과)"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — 인증 필요"),
            @ApiResponse(responseCode = "403", description = "MEMBER_DEACTIVATED — 탈퇴한 회원"),
            @ApiResponse(responseCode = "409", description = "HANDLE_CHANGE_TOO_SOON(30일 이내 재변경), "
                    + "DUPLICATE_HANDLE(다른 회원이 쓰거나 쓴 적 있는 handle), "
                    + "HANDLE_CHANGE_CONFLICT(같은 회원의 동시 변경 요청)")
    })
    @PatchMapping("/me/handle")
    public com.atcrew.common.response.ApiResponse<HandleChangeInfo> changeHandle(
            @RequestBody @Valid ChangeHandleRequest request) {
        return com.atcrew.common.response.ApiResponse.success(
                memberService.changeHandle(securityUtils.getCurrentMemberId(), request.handle()));
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
            @ApiResponse(responseCode = "400", description = "입력값 오류(COMMON_INVALID_INPUT), "
                    + "종료일 누락·시작일보다 앞선 종료일(INVALID_CAREER_PERIOD), "
                    + "회원 시간대 기준 미래의 시작일·종료일(CAREER_DATE_IN_FUTURE), 경력 50개 초과(CAREER_LIMIT_EXCEEDED)"),
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
