package com.atcrew.auth.internal.web;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.AuthService;
import com.atcrew.auth.EmailLoginCommand;
import com.atcrew.auth.EmailRegisterCommand;
import com.atcrew.auth.GoogleRegisterCommand;
import com.atcrew.auth.internal.web.dto.EmailLoginRequest;
import com.atcrew.auth.internal.web.dto.EmailRegisterRequest;
import com.atcrew.auth.internal.web.dto.GoogleLoginRequest;
import com.atcrew.auth.internal.web.dto.GoogleRegisterRequest;
import com.atcrew.auth.internal.web.dto.RefreshRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ─── 이메일 인증 ─────────────────────────────────────────────────────

    @Operation(summary = "이메일 로그인",
            description = """
                    이메일·비밀번호로 로그인하고 Access/Refresh Token과 회원 정보를 반환합니다. 인증이 필요 없는 공개 API입니다.
                    로그인 실패 사유(미가입·탈퇴·비밀번호 오류)는 계정 존재 여부 노출을 막기 위해 401 AUTHENTICATION_FAILED 하나로 통합됩니다.
                    같은 이메일로 5회 연속 실패하면 10분 동안 429로 차단됩니다.""")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "AUTHENTICATION_FAILED — 이메일 또는 비밀번호 불일치 (미가입·탈퇴 포함)")
    @ApiResponse(responseCode = "428", description = "PASSWORD_RESET_REQUIRED — 비밀번호 재설정 필요 (라이트에서 이관된 회원)")
    @ApiResponse(responseCode = "429", description = "TOO_MANY_ATTEMPTS — 로그인 시도 횟수 초과 (이메일 5회·IP 30회 / 10분)")
    @PostMapping("/email/login")
    public com.atcrew.common.response.ApiResponse<AuthInfo> emailLogin(
            @RequestBody @Valid EmailLoginRequest request) {
        EmailLoginCommand command = new EmailLoginCommand(request.email(), request.password());
        return com.atcrew.common.response.ApiResponse.success(authService.loginWithEmail(command));
    }

    @Operation(summary = "이메일 회원가입",
            description = """
                    이메일·비밀번호로 회원가입합니다. 인증이 필요 없는 공개 API이며, 가입 즉시 활성화되고 로그인과 동일하게
                    Access/Refresh Token과 회원 정보를 반환합니다(isNewUser=true). @핸들은 서버가 자동 생성합니다.
                    400 상세 코드: TERMS_NOT_AGREED(필수 약관 미동의) · INVALID_TIMEZONE · INVALID_COUNTRY ·
                    COMMON_INVALID_INPUT(형식 오류·비밀번호 확인 불일치). 409는 DUPLICATE_EMAIL이며,
                    중복 판정은 가입 경로별이라 탈퇴한 계정의 이메일로는 다시 가입할 수 있습니다.""")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/email/register")
    public com.atcrew.common.response.ApiResponse<AuthInfo> emailRegister(
            @RequestBody @Valid EmailRegisterRequest request) {
        EmailRegisterCommand command = new EmailRegisterCommand(
                request.email(), request.password(), request.name(),
                request.agreeService(), request.agreePrivacy(),
                request.agreeThirdParty(), request.agreeMarketing(), request.timezone(), request.countryCode());
        return com.atcrew.common.response.ApiResponse.success(authService.registerWithEmail(command));
    }

    // ─── Google 인증 ─────────────────────────────────────────────────────

    @Operation(summary = "Google 로그인",
            description = """
                    Firebase ID Token으로 Google 로그인합니다. 인증이 필요 없는 공개 API입니다.
                    Google로 가입한 적이 없으면 404 MEMBER_NOT_REGISTERED를 반환하므로 프론트는 Google 회원가입 화면으로 이동시킵니다
                    (Firebase 토큰이 이메일 소유를 증명하므로 계정 존재 노출로 보지 않습니다).
                    같은 이메일이라도 이메일 가입 계정은 이 API로 로그인되지 않고 404가 됩니다.""")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "INVALID_FIREBASE_TOKEN — Firebase 토큰 검증 실패")
    @ApiResponse(responseCode = "503", description = "FIREBASE_NOT_CONFIGURED — 서버에 Firebase 자격증명이 설정되지 않음 (로컬 환경 기본값)")
    @PostMapping("/google/login")
    public com.atcrew.common.response.ApiResponse<AuthInfo> googleLogin(
            @RequestBody @Valid GoogleLoginRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.loginWithGoogle(request.firebaseIdToken()));
    }

    @Operation(summary = "Google 회원가입",
            description = """
                    Firebase ID Token으로 Google 계정 회원가입합니다. 인증이 필요 없는 공개 API이며, 이메일은 토큰에서 추출하므로
                    별도로 보내지 않습니다. 가입 즉시 활성화되고 Access/Refresh Token과 회원 정보를 반환합니다(isNewUser=true).
                    400 상세 코드: TERMS_NOT_AGREED · INVALID_TIMEZONE · INVALID_COUNTRY · COMMON_INVALID_INPUT.
                    409는 DUPLICATE_EMAIL(이미 가입된 Google 계정)입니다.""")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "401", description = "INVALID_FIREBASE_TOKEN — Firebase 토큰 검증 실패")
    @ApiResponse(responseCode = "503", description = "FIREBASE_NOT_CONFIGURED — 서버에 Firebase 자격증명이 설정되지 않음 (로컬 환경 기본값)")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/google/register")
    public com.atcrew.common.response.ApiResponse<AuthInfo> googleRegister(
            @RequestBody @Valid GoogleRegisterRequest request) {
        GoogleRegisterCommand command = new GoogleRegisterCommand(
                request.firebaseIdToken(), request.name(),
                request.agreeService(), request.agreePrivacy(),
                request.agreeThirdParty(), request.agreeMarketing(), request.timezone(), request.countryCode());
        return com.atcrew.common.response.ApiResponse.success(authService.registerWithGoogle(command));
    }

    // ─── 공통 ─────────────────────────────────────────────────────────────

    @Operation(summary = "토큰 갱신",
            description = """
                    Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다. Authorization 헤더 없이 호출하는 공개 API입니다.
                    Refresh Token은 1회용이라 갱신에 성공하면 즉시 폐기되므로, 응답으로 받은 새 refreshToken으로 교체 저장해야 합니다.
                    이미 사용했거나 만료·위조된 토큰, 탈퇴한 회원의 토큰은 모두 401 INVALID_REFRESH_TOKEN입니다.""")
    @ApiResponse(responseCode = "200", description = "토큰 갱신 성공")
    @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN — 유효하지 않거나 이미 사용·만료된 Refresh Token")
    @PostMapping("/refresh")
    public com.atcrew.common.response.ApiResponse<AuthInfo> refresh(@RequestBody @Valid RefreshRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.refresh(request.refreshToken()));
    }
}
