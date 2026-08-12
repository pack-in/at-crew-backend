package com.atcrew.member.internal.web;

import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.MemberProfileInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.internal.web.dto.AddCareerRequest;
import com.atcrew.member.internal.web.dto.UpdateInfoRequest;
import com.atcrew.member.internal.web.dto.UpdateNameRequest;
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

    @Operation(summary = "핸들로 회원 조회",
            description = """
                    @핸들로 회원의 공개 프로필을 조회합니다. 인증 없이 호출할 수 있고, 토큰을 함께 보내면 본인 조회를 구분해
                    작가 조회 이력에서 제외합니다(응답 내용은 동일). 탈퇴한 회원과 존재하지 않는 핸들은 모두 404 MEMBER_NOT_FOUND입니다.
                    핸들 형식(영문·숫자·_·- 3~30자)에 어긋나면 400입니다.""")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{handle}")
    public com.atcrew.common.response.ApiResponse<MemberProfileInfo> findByHandle(
            @Parameter(description = "회원 핸들 (@ 제외, 영문·숫자·_·- 3~30자)", example = "user_a580e617")
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{3,30}$", message = "핸들 형식이 올바르지 않습니다") String handle) {
        return com.atcrew.common.response.ApiResponse.success(
                memberService.findProfileByHandle(handle, getOptionalMemberId()));
    }

    @Operation(summary = "이름 수정",
            description = """
                    로그인한 회원 본인의 이름·작가명(최대 16자)을 수정합니다. 성공 시 응답 본문이 없습니다(204).""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 성공 (응답 본문 없음)"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — Access Token 누락·만료·위조"),
            @ApiResponse(responseCode = "403", description = "MEMBER_DEACTIVATED — 탈퇴한 회원")
    })
    @PatchMapping("/me/name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateName(@RequestBody @Valid UpdateNameRequest request) {
        memberService.updateName(securityUtils.getCurrentMemberId(), request.name());
    }

    @Operation(summary = "프로필 정보 수정",
            description = """
                    로그인한 회원 본인의 창작자 유형·구인구직 상태·활동 분야·경력 연차·활동 지역·슬롯·팀 작업 경험·
                    연락처·SNS·툴·시간대·거주 국가를 부분 수정합니다. 보내지 않은(또는 null인) 필드는 기존 값이 유지됩니다.
                    성공 시 응답 본문이 없으므로(204) 최신 값이 필요하면 GET /api/members/{handle}로 다시 조회합니다.
                    400 상세 코드: INVALID_SLOT_COUNT · INVALID_TIMEZONE · INVALID_COUNTRY · COMMON_INVALID_INPUT.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 성공 (응답 본문 없음)"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — Access Token 누락·만료·위조"),
            @ApiResponse(responseCode = "403", description = "MEMBER_DEACTIVATED — 탈퇴한 회원")
    })
    @PatchMapping("/me/info")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateInfo(@RequestBody @Valid UpdateInfoRequest request) {
        memberService.updateInfo(securityUtils.getCurrentMemberId(), new UpdateInfoCommand(
                request.creatorRole(), request.employmentStatus(),
                request.activityFields(), request.experienceLevel(), request.activeRegions(),
                request.totalSlotCount(), request.availableSlotCount(), request.teamExperiences(),
                request.contact(), request.sns(), request.tools(), request.timezone(), request.countryCode()));
    }

    @Operation(summary = "경력 추가",
            description = """
                    로그인한 회원 본인의 참여작 정보를 경력으로 추가하고, 생성된 경력 1건을 반환합니다(삭제에 필요한 id 포함).
                    날짜는 ISO 8601이 아닌 yyyy.MM.dd 포맷만 허용하며 회원당 최대 50개까지 등록할 수 있습니다.
                    400 상세 코드: INVALID_CAREER_PERIOD(종료일 누락·역전) · CAREER_LIMIT_EXCEEDED(50개 초과) · COMMON_INVALID_INPUT.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "경력 추가 성공"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — Access Token 누락·만료·위조"),
            @ApiResponse(responseCode = "403", description = "MEMBER_DEACTIVATED — 탈퇴한 회원")
    })
    @PostMapping("/me/careers")
    @ResponseStatus(HttpStatus.CREATED)
    public com.atcrew.common.response.ApiResponse<CareerEntryInfo> addCareer(@RequestBody @Valid AddCareerRequest request) {
        return com.atcrew.common.response.ApiResponse.success(
                memberService.addCareer(securityUtils.getCurrentMemberId(), new AddCareerCommand(
                        request.workTitle(), request.role(), request.startDate(),
                        request.endDate(), request.ongoing(), request.description())));
    }

    @Operation(summary = "경력 삭제",
            description = """
                    로그인한 회원 본인의 경력 항목을 삭제합니다. 성공 시 응답 본문이 없습니다(204).
                    본인 소유가 아니거나 이미 삭제된 경력 ID는 404 CAREER_NOT_FOUND입니다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공 (응답 본문 없음)"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — Access Token 누락·만료·위조"),
            @ApiResponse(responseCode = "403", description = "MEMBER_DEACTIVATED — 탈퇴한 회원")
    })
    @DeleteMapping("/me/careers/{careerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCareer(
            @Parameter(description = "삭제할 경력 ID (경력 추가 응답의 id)",
                    example = "019ff381-833e-7235-9568-402dcfcb6efb")
            @PathVariable String careerId) {
        memberService.deleteCareer(securityUtils.getCurrentMemberId(), careerId);
    }

    @Operation(summary = "회원 탈퇴",
            description = """
                    로그인한 회원 본인의 계정을 비활성화(소프트 딜리트) 처리합니다. 성공 시 응답 본문이 없습니다(204).
                    탈퇴하면 발급된 Refresh Token이 모두 폐기되고 공개 프로필 조회는 404가 되며,
                    남아 있는 Access Token으로 다른 회원 API를 호출하면 403 MEMBER_DEACTIVATED입니다.
                    이미 탈퇴한 상태에서 다시 호출해도 204(멱등)를 반환합니다. 같은 이메일로 재가입할 수 있습니다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "탈퇴 처리 성공 (응답 본문 없음, 재호출도 204)"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED — Access Token 누락·만료·위조")
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
