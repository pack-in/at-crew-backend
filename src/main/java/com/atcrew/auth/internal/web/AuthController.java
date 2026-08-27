package com.atcrew.auth.internal.web;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.AuthService;
import com.atcrew.auth.EmailLoginCommand;
import com.atcrew.auth.EmailRegisterCommand;
import com.atcrew.auth.GoogleRegisterCommand;
import com.atcrew.auth.internal.web.dto.ChangePasswordRequest;
import com.atcrew.auth.internal.web.dto.EmailLoginRequest;
import com.atcrew.auth.internal.web.dto.EmailRegisterRequest;
import com.atcrew.auth.internal.web.dto.GoogleLoginRequest;
import com.atcrew.auth.internal.web.dto.GoogleRegisterRequest;
import com.atcrew.auth.internal.web.dto.LogoutRequest;
import com.atcrew.auth.internal.web.dto.PasswordResetConfirmRequest;
import com.atcrew.auth.internal.web.dto.PasswordResetRequestRequest;
import com.atcrew.auth.internal.web.dto.RefreshRequest;
import com.atcrew.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "로그인·회원가입·토큰 갱신 API")
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;

    AuthController(AuthService authService, SecurityUtils securityUtils) {
        this.authService = authService;
        this.securityUtils = securityUtils;
    }

    // ─── 이메일 인증 ─────────────────────────────────────────────────────

    @Operation(summary = "이메일 로그인",
            description = "이메일·비밀번호로 로그인합니다. 로그인 실패 사유(미가입·탈퇴·비밀번호 오류)는 보안상 단일 오류 코드로 통합됩니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "400", description = "입력 형식 오류")
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    @ApiResponse(responseCode = "428", description = "비밀번호 재설정 필요 (마이그레이션 회원)")
    @ApiResponse(responseCode = "429", description = "로그인 시도 횟수 초과")
    @PostMapping("/email/login")
    public com.atcrew.common.response.ApiResponse<AuthInfo> emailLogin(
            @RequestBody @Valid EmailLoginRequest request) {
        EmailLoginCommand command = new EmailLoginCommand(request.email(), request.password());
        return com.atcrew.common.response.ApiResponse.success(authService.loginWithEmail(command));
    }

    @Operation(summary = "이메일 회원가입",
            description = "이메일·비밀번호로 회원가입합니다. 가입 즉시 활성화됩니다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "입력 형식 오류·비밀번호 불일치·주 사용 언어 미선택(PRIMARY_LANGUAGE_REQUIRED)")
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/email/register")
    public com.atcrew.common.response.ApiResponse<AuthInfo> emailRegister(
            @RequestBody @Valid EmailRegisterRequest request) {
        EmailRegisterCommand command = new EmailRegisterCommand(
                request.email(), request.password(), request.name(),
                request.agreeService(), request.agreePrivacy(),
                request.agreeThirdParty(), request.agreeMarketing(), request.timezone(), request.countryCode(),
                request.primaryLanguage());
        return com.atcrew.common.response.ApiResponse.success(authService.registerWithEmail(command));
    }

    @Operation(summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다. 이메일 가입 계정 전용이며, "
                    + "변경 후에는 기존 Refresh Token이 모두 폐기되므로 다시 로그인해야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "입력 형식 오류·현재 비밀번호 불일치·소셜 로그인 계정"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "428", description = "비밀번호 재설정 필요 (마이그레이션 회원)")
    })
    @PostMapping("/email/password-change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(securityUtils.getCurrentMemberId(),
                request.currentPassword(), request.newPassword());
    }

    @Operation(summary = "비밀번호 재설정 요청",
            description = "가입 여부와 무관하게 항상 200을 반환합니다(계정 존재 노출 방지, "
                    + "docs/design/auth-email-custom-redesign.md §7.2). "
                    + "EMAIL 가입 계정이면 재설정 링크를, 동일 이메일의 Google 계정만 있으면 안내 메일을 발송합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 접수 (메일 발송 여부와 무관하게 항상 반환)"),
            @ApiResponse(responseCode = "400", description = "입력 형식 오류"),
            @ApiResponse(responseCode = "429", description = "요청 횟수 초과")
    })
    @PostMapping("/email/password-reset/request")
    public void requestPasswordReset(@RequestBody @Valid PasswordResetRequestRequest request) {
        authService.requestPasswordReset(request.email());
    }

    @Operation(summary = "비밀번호 재설정 확정",
            description = "메일로 받은 토큰과 새 비밀번호로 재설정을 완료합니다. 토큰은 1회용이며, "
                    + "성공 시 기존 Refresh Token이 모두 폐기되므로 다시 로그인해야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "재설정 성공"),
            @ApiResponse(responseCode = "400", description = "입력 형식 오류·비밀번호 불일치"),
            @ApiResponse(responseCode = "401", description = "토큰이 유효하지 않거나 만료됨")
    })
    @PostMapping("/email/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@RequestBody @Valid PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
    }

    // ─── Google 인증 ─────────────────────────────────────────────────────

    @Operation(summary = "Google 로그인",
            description = "Firebase ID Token으로 Google 로그인합니다. 미가입 시 404를 반환하므로 프론트가 가입 화면으로 이동합니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "Firebase 토큰 검증 실패")
    @ApiResponse(responseCode = "404", description = "미가입 계정 (가입 화면으로 이동)")
    @PostMapping("/google/login")
    public com.atcrew.common.response.ApiResponse<AuthInfo> googleLogin(
            @RequestBody @Valid GoogleLoginRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.loginWithGoogle(request.firebaseIdToken()));
    }

    @Operation(summary = "Google 회원가입",
            description = "Firebase ID Token으로 Google 계정 회원가입합니다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "주 사용 언어 미선택(PRIMARY_LANGUAGE_REQUIRED)")
    @ApiResponse(responseCode = "401", description = "Firebase 토큰 검증 실패")
    @ApiResponse(responseCode = "409", description = "이미 가입된 Google 계정")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/google/register")
    public com.atcrew.common.response.ApiResponse<AuthInfo> googleRegister(
            @RequestBody @Valid GoogleRegisterRequest request) {
        GoogleRegisterCommand command = new GoogleRegisterCommand(
                request.firebaseIdToken(), request.name(),
                request.agreeService(), request.agreePrivacy(),
                request.agreeThirdParty(), request.agreeMarketing(), request.timezone(), request.countryCode(),
                request.primaryLanguage());
        return com.atcrew.common.response.ApiResponse.success(authService.registerWithGoogle(command));
    }

    // ─── 공통 ─────────────────────────────────────────────────────────────

    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다.")
    @ApiResponse(responseCode = "200", description = "토큰 갱신 성공")
    @PostMapping("/refresh")
    public com.atcrew.common.response.ApiResponse<AuthInfo> refresh(@RequestBody @Valid RefreshRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃",
            description = "전달한 Refresh Token을 폐기합니다. Access Token은 상태 없는 JWT라 만료까지 유효하므로 "
                    + "클라이언트가 즉시 폐기해야 합니다. 이미 로그아웃된 상태여도 204를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공 (이미 로그아웃된 경우 포함)"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(securityUtils.getCurrentMemberId(), request.refreshToken());
    }
}
