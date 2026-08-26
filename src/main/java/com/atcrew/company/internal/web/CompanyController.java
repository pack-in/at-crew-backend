package com.atcrew.company.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.company.AddCompanyCareerCommand;
import com.atcrew.company.CompanyCareerInfo;
import com.atcrew.company.CompanyInfo;
import com.atcrew.company.CompanyService;
import com.atcrew.company.UpdateCompanyInfoCommand;
import com.atcrew.company.internal.web.dto.AddCompanyCareerRequest;
import com.atcrew.company.internal.web.dto.CreateCompanyRequest;
import com.atcrew.company.internal.web.dto.UpdateCompanyInfoRequest;
import com.atcrew.company.internal.web.dto.UpdateCompanyNameRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 기업 마이페이지 API. 공개 조회(GET)는 비로그인도 가능하며, 수정·업로드 액션은 본인 기업 계정만 호출할 수 있다
 * (docs/design/company-profile-module-design.md §4, §5).
 */
@Tag(name = "기업 프로필", description = "기업 마이페이지 — 기업 프로필 생성·조회·수정·경력 관리 API")
@Validated
@RestController
@RequestMapping("/api/companies")
class CompanyController {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final CompanyService companyService;
    private final SecurityUtils securityUtils;

    CompanyController(CompanyService companyService, SecurityUtils securityUtils) {
        this.companyService = companyService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "기업 프로필 생성", description = "로그인한 회원의 기업 프로필을 생성합니다. 회원당 1개만 생성할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 기업 프로필 보유")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompanyInfo> createCompany(@RequestBody @Valid CreateCompanyRequest request) {
        return ApiResponse.success(
                companyService.create(securityUtils.getCurrentMemberId(), request.companyName()));
    }

    @Operation(summary = "기업 프로필 공개 조회",
            description = "기업 마이페이지를 조회합니다. 인증 불필요하며, 본인 기업이면 응답의 isOwner가 true입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{companyId}")
    public ApiResponse<CompanyInfo> getCompany(
            @Parameter(description = "기업 프로필 ID") @PathVariable
            @Pattern(regexp = UUID_PATTERN, message = "기업 프로필 ID 형식이 올바르지 않습니다") String companyId) {
        return ApiResponse.success(companyService.findById(companyId, getOptionalMemberId()));
    }

    @Operation(summary = "내 기업 프로필 조회", description = "로그인한 회원이 소유한 기업 프로필을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "기업 프로필 없음")
    })
    @GetMapping("/me")
    public ApiResponse<CompanyInfo> getMyCompany() {
        return ApiResponse.success(companyService.findByMemberId(securityUtils.getCurrentMemberId()));
    }

    @Operation(summary = "기업명 수정", description = "본인 기업의 기업명을 수정합니다. (최대 16자)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateName(@RequestBody @Valid UpdateCompanyNameRequest request) {
        companyService.updateName(securityUtils.getCurrentMemberId(), request.companyName());
    }

    @Operation(summary = "기업 정보 수정",
            description = "구인구직 상태·회사 형태·활동 분야·연락처·SNS·사업자 등록 여부를 수정합니다. null인 필드는 변경하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/info")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateInfo(@RequestBody @Valid UpdateCompanyInfoRequest request) {
        companyService.updateInfo(securityUtils.getCurrentMemberId(), new UpdateCompanyInfoCommand(
                request.recruitStatus(), request.companyType(),
                request.activityField(),
                request.contact(), request.sns(), request.hasBusinessRegistration()));
    }

    @Operation(summary = "기업 경력 추가", description = "본인 기업의 참여작 경력을 추가합니다. (최대 50개)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/me/careers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompanyCareerInfo> addCareer(@RequestBody @Valid AddCompanyCareerRequest request) {
        return ApiResponse.success(companyService.addCareer(
                securityUtils.getCurrentMemberId(),
                new AddCompanyCareerCommand(request.workTitle(), request.startDate(),
                        request.endDate(), request.ongoing(), request.description())));
    }

    @Operation(summary = "기업 경력 목록 조회", description = "기업의 참여작 경력을 최신 시작일 순으로 조회합니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{companyId}/careers")
    public ApiResponse<List<CompanyCareerInfo>> getCareers(
            @Parameter(description = "기업 프로필 ID") @PathVariable
            @Pattern(regexp = UUID_PATTERN, message = "기업 프로필 ID 형식이 올바르지 않습니다") String companyId) {
        return ApiResponse.success(companyService.listCareers(companyId));
    }

    // 공개 GET에서 로그인 여부를 판별한다 — 비로그인이면 null (artwork 모듈과 동일 패턴).
    private String getOptionalMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof MemberPrincipal principal) {
            return principal.memberId();
        }
        return null;
    }
}
